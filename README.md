# Discord Thread Scheduler

A simple command-line application that creates threads on Discord, optionally pinning and unpinning the most recent 
ones. You can configure as many unique threads as you want.

## Usage

1. Set the `DISCORD_THREAD_SCHEDULER_TOKEN` environment variable to your Discord bot token. Optionally, you can also use
the `-t TOKEN` or `-f TOKEN_FILE` arguments.
2. Create a config file. By default, the application searches for `config.json` in your current directory, but this may
can be changed with the `-c CONFIG_FILE` argument.
3. Run the application

## Configuration

Here is an example configuration file:

```json
{
  "timezone": "Europe/Helsinki",
  "channels": {
    "1234567890123456789": {
      "threads": {
        "daily": {
          "title": "Daily thread (%yy-%mm-%dd)",
          "pin": {
            "pin": true,
            "unpin": true
          },
          "period": {
            "type": "daily",
            "time": {
              "hour": 12
            }
          }
        },
        "weekly": {
          "title": "Weekly thread (%yy-%mm-%dd)",
          "pin": {
            "pin": true,
            "unpin": false
          },
          "period": {
            "type": "weekly",
            "time": {
              "hour": 14,
              "minute": 30
            },
            "day": "MONDAY"
          }
        },
        "monthly": {
          "title": "Monthly thread (%yy-%mm-%dd)",
          "period": {
            "type": "monthly",
            "time": {
              "hour": 20,
              "minute": 42,
              "second": 37
            },
            "day": 15
          }
        }
      }
    }
  }
}
```

- `timezone`: Sets the timezone to be used for date calculations. May be omitted, default is UTC
- `channels`: Every key is a Discord channel id
- `threads`: Every key is an id for a schedule. This id is only used in a filename to store last pinned message's id for 
unpinning
- `title`: Title for the thread. This string supports some variables:
  - `$yy`: Last two digits of the current year
  - `$y`: The current year
  - `$mm`: The current month, padded to two digits
  - `$m`: The current month
  - `$M`: The current month's name
  - `$dd`: The current day, padded to two digits
  - `$d`: The current day
  - `$D`: The current day's name
- `pin`: Pinning data. May be omitted, default is no pinning. Unpin is ignored if pin is false
- `period`: When and how often a new thread should be created. Supports three types:
  - `daily`: Creates a thread every day. Only takes in the time of day
  - `weekly`: Creates a thread every week. Takes in the time of day and a day of the week
  - `monthly`: Creates a thread every month. Takes in the time of day and a day of month. Only use days that are in 
every month (1-28)
- `time`: Time of day. Minutes and seconds may be omitted, both default to `0`