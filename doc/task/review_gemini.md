# 1. Top Bugs & Code-Quality Issues

1. **Hardcoded Text & Broken i18n (`app/src/main/java/com/samsunggalaxy/ui/ResultAct.kt:492`)**  
   BMI category strings are hardcoded in English (e.g., `"You are Under Weight"`). This breaks localization entirely on the core results page for an app supporting 7 languages.
2. **Hardcoded Sedentary TDEE (`app/src/main/java/com/samsunggalaxy/ui/ResultAct.kt:508`)**  
   The main insights panel statically calculates TDEE with an activity level of `0` (`calculateTDEE(bmr, 0)`), providing wildly inaccurate daily calorie estimates for any active user.
3. **Arbitrary Weight Picker Limits (`app/src/main/java/com/samsunggalaxy/ui/MainAct.kt:329`)**  
   The wheel picker populates via `getData(151)`, capping the max selectable weight at 151kg (~332 lbs). This artificially locks out severely obese users who most need a tracking tool.
4. **Magic Index for Default Age (`app/src/main/java/com/samsunggalaxy/ui/MainAct.kt:374`)**  
   The default age is hardcoded to index `15` (`currentIndex = 15 // Default 25 years`). This fragile magic number will silently break the default UI age if the underlying array bounds ever change.

# 2. Top "Half-Finished or Dead" Features

1. **Reward Ad Detailed Plan (`ResultAct.kt:152`)**  
   The UI trigger is commented out (`// setupRewardButton()`), and a note explicitly says `// Reward Ad — deferred until SDK adds showRewardedAd support`. However, the translation strings for this (e.g., `reward_ad_skipped`) already exist in all `strings.xml` variants.
2. **Disconnected TDEE Calculator Flow (`TdeeCalculatorActivity.kt`)**  
   A fully functional TDEE calculator with an activity level dropdown exists. Yet, the `Profile` database and the main dashboard completely ignore it, forcing all users into a "Sedentary" activity level on the main results screen rather than letting them save their baseline.

# 3. Recommended New Features (Weight Tracking)

1. **Interactive Weight Forecasting/Projection**  
   *Pro*: Highly engaging; predicting when users will hit their goal weight based on recent trends acts as a massive retention hook.  
   *Con*: Requires complex regression logic to handle daily weight fluctuations without creating volatile, frustrating charts.
2. **Progress Photo Gallery**  
   *Pro*: Provides strong visual reinforcement for weight management alongside raw numbers.  
   *Con*: Introduces storage permission complexities and drastically increases the app's disk footprint.
3. **Smart Daily Weigh-in Notifications**  
   *Pro*: Drives daily active usage (DAU) and directly feeds into the new Streak Gamification system.  
   *Con*: High risk of notification fatigue; requires handling Android 13+ `POST_NOTIFICATIONS` permissions.

# 4. "Flagship/Exclusive" Differentiators

1. **Offline-first Insights Engine (Local)**  
   Build a completely on-device engine that compares the user's current stats against global health guidelines to generate localized, dynamic daily advice—without relying on any backend API or data sharing.
2. **Export to Doctor PDF/CSV (Local)**  
   Generate a medical-friendly, printable report of the weight/BMI trend over 30/90 days. This pivots the app from a casual calculator into a legitimate medical tracking tool.
3. **Wearable Sync via Health Connect (Requires Infrastructure/APIs)**  
   Pull step counts and active calories from Health Connect to dynamically calculate TDEE every day, automatically replacing the static activity level dropdowns.

# 5. Explicitly Do NOT Build

**Social Feed / Leaderboards**  
Weight and BMI are highly sensitive personal metrics. A social feature requires heavy backend moderation, user accounts, server infrastructure, and brings massive privacy/GDPR liabilities. It completely violates the app's current lightweight, local-only architecture with near-zero ROI.
