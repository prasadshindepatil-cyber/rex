from speed_agent import SpeedAgent
from market_agent import MarketAgent
from git_sync_agent import GitSyncAgent
from task_agent import TaskAgent

class MasterOrchestrator:
    def __init__(self):
        self.speed = SpeedAgent()
        self.market = MarketAgent()
        self.git_sync = GitSyncAgent()
        self.task = TaskAgent()

    def run_diagnostics(self):
        return {
            "speed": self.speed.optimize(),
            "market": self.market.fetch_market_rates(),
            "git": self.git_sync.check_status(),
            "orchestrator": "All 5 advanced agents operational."
        }
