---
name: weather-retrieval
description: Retrieve current weather information for a specific location. Use when a user asks for weather, temperature, conditions, forecast summary, or climate details for a city, region, or place.
---

Get weather details for the location the user provides.

1. Extract the location from the user request.
2. If the location is missing or ambiguous, ask for a clearer location name.
3. Fetch the weather data by passing the location to `scripts/retrieve_weather.py`
4. Parse the JSON output from the script.
5. Return a concise summary that includes:
    - location name
    - temperature
    - weather condition
    - any notable context (for example, wind, humidity, or precipitation)

If the script fails or returns invalid data, explain that clearly and suggest trying a more specific location.
