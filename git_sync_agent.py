import subprocess

class GitSyncAgent:
    def __init__(self):
        self.name = "GitSyncAgent"

    def check_status(self):
        try:
            res = subprocess.run(["git", "status"], capture_output=True, text=True, timeout=5)
            return {"status": "success", "output": res.stdout}
        except Exception as e:
            return {"status": "error", "message": str(e)}
