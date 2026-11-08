class MarketAgent:
    def __init__(self):
        self.name = "MarketAgent"

    def fetch_market_rates(self, crop="सोयाबीन"):
        # Advanced market rate retrieval simulation based on Agmarknet/MSAMB standards
        return {
            "crop": crop,
            "avg_price": "₹4,300 - ₹4,650 per quintal",
            "source": "Agmarknet / MSAMB Official Data",
            "status": "success"
        }
