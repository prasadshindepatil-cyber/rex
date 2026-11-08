class TaskAgent:
    def __init__(self):
        self.name = "TaskAgent"

    def parse_and_execute(self, command):
        # Quick intent parser for Rex tasks
        return {
            "command": command,
            "executed": True,
            "result": f"Task '{command}' processed successfully by TaskAgent."
        }
