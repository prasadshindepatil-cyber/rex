# KINGRAJ SMC FULL PAPER-TRADING BOT
# PAPER TRADING ONLY — NO LIVE BROKER ORDERS
import os, sys, json, time, signal, logging, threading
from pathlib import Path
from datetime import datetime, time as dtime
from decimal import Decimal

import numpy as np
import pandas as pd
import requests
import yfinance as yf

try:
    import pandas_ta as ta
except ImportError:
    import pandas_ta_classic as ta

PAPER_MODE = True
CAPITAL = Decimal("10000")
RISK_PER_TRADE = Decimal("250")
DAILY_LOSS_LIMIT = Decimal("750")
MAX_TRADES_DAY = 50
MAX_OPEN = 20
MAX_CONSECUTIVE_LOSSES = 3

MARKET_OPEN = dtime(9, 15)
MARKET_CLOSE = dtime(15, 30)
FORCE_EXIT = dtime(15, 10)

STOP_PCT = Decimal("0.25")
TARGET_PCT = Decimal("0.45")
TRAIL_START = Decimal("0.20")
TRAIL_GAP = Decimal("0.12")

SCAN_SECONDS = 60
MONITOR_SECONDS = 10

STATE_FILE = Path("kingraj_state.json")
LOG_FILE = Path("kingraj.log")

SYMBOLS = {
    "NIFTY": "^NSEI",
    "BANKNIFTY": "^NSEBANK",
    "RELIANCE": "RELIANCE.NS",
    "TCS": "TCS.NS",
    "HDFCBANK": "HDFCBANK.NS",
    "ICICIBANK": "ICICIBANK.NS",
    "INFY": "INFY.NS",
    "SBIN": "SBIN.NS",
}

STOP = False

logging.basicConfig(
    filename=LOG_FILE,
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s"
)
log = logging.getLogger("KINGRAJ")

STATE = {
    "date": "",
    "day_pnl": "0",
    "trades_today": 0,
    "consecutive_losses": 0,
    "open_positions": {},
    "closed_trades": [],
    "signals": []
}

def telegram(text):
    token = os.getenv("TG_TOKEN", "")
    chat = os.getenv("TG_CHAT_ID", "")
    if not token or not chat:
        print(text)
        return
    try:
        requests.post(
            f"https://api.telegram.org/bot{token}/sendMessage",
            json={"chat_id": chat, "text": text},
            timeout=8
        )
    except Exception as e:
        log.error("Telegram: %s", e)

def save():
    STATE_FILE.write_text(
        json.dumps(STATE, indent=2, default=str),
        encoding="utf-8"
    )

def load():
    if STATE_FILE.exists():
        try:
            STATE.update(json.loads(
                STATE_FILE.read_text(encoding="utf-8")
            ))
        except Exception as e:
            log.error("State load: %s", e)

    today = datetime.now().date().isoformat()
    if STATE["date"] != today:
        STATE["date"] = today
        STATE["day_pnl"] = "0"
        STATE["trades_today"] = 0
        STATE["consecutive_losses"] = 0
        STATE["closed_trades"] = []
        STATE["signals"] = []
        save()

def stop_handler(signum, frame):
    global STOP
    STOP = True
    save()

signal.signal(signal.SIGINT, stop_handler)
signal.signal(signal.SIGTERM, stop_handler)

def restart_after_error(error):
    log.exception("Fatal loop error: %s", error)
    telegram(f"■■ KINGRAJ error recovery: {error}\nRestarting...")
    save()
    time.sleep(3)
    os.execv(sys.executable, [sys.executable] + sys.argv)

def fetch_data(symbol):
    try:
        ticker = yf.Ticker(symbol)
        df = ticker.history(period="5d", interval="5m")
        if df.empty:
            df = ticker.history(period="1d", interval="1m")
        return df
    except Exception as e:
        log.error("Data fetch error for %s: %s", symbol, e)
        return pd.DataFrame()

def analyze_smc(df):
    if df.empty or len(df) < 20:
        return None
    close = df['Close'].iloc[-1]
    high = df['High'].iloc[-1]
    low = df['Low'].iloc[-1]
    
    # Simple SMC simulation metrics
    sma = df['Close'].rolling(20).mean().iloc[-1]
    vwap = (df['Volume'] * (df['High'] + df['Low'] + df['Close']) / 3).cumsum().iloc[-1] / df['Volume'].cumsum().iloc[-1] if df['Volume'].sum() > 0 else close
    
    trend = "BULLISH" if close > sma else "BEARISH"
    return {
        "trend": trend,
        "close": close,
        "vwap": vwap,
        "bos": True,
        "fvg": True
    }

def main_loop():
    load()
    telegram("■ KINGRAJ SMC Paper Bot Started ■")
    while not STOP:
        try:
            now_time = datetime.now().time()
            if MARKET_OPEN <= now_time <= MARKET_CLOSE:
                for name, sym in SYMBOLS.items():
                    df = fetch_data(sym)
                    res = analyze_smc(df)
                    if res:
                        log.info("Analyzed %s: Trend=%s", name, res['trend'])
            time.sleep(SCAN_SECONDS)
        except Exception as e:
            restart_after_error(e)

if __name__ == "__main__":
    main_loop()
