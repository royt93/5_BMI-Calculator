# Bugs Fixed - Logic Issues

## 🐛 Issues Found & Fixed

### 1. **Default Profile Not Created** ⚠️ CRITICAL
**Issue**: App uses `profileId = 0` for saving records, but Room auto-increment starts at 1.
**Impact**: Records could not be saved or linked to any profile.
**Fix**:
- Added `initializeDefaultProfile()` in `GalaxyApp.onCreate()`
- Creates default profile on first app launch
- Updated `saveToHistory()` to get current profile or default to ID = 1
- Updated `HistoryActivity.loadData()` to use correct profile ID

**Files Modified**:
- `GalaxyApp.kt` - Added profile initialization
- `ResultAct.kt` - Updated saveToHistory() to fetch current profile
- `HistoryActivity.kt` - Fixed profile ID lookup

### 2. **Height Default Value Was Wrong** ⚠️ MODERATE
**Issue**: `height = 1` as default value, which is invalid.
**Impact**: If height wheel doesn't load, BMI calculation uses height = 1cm (invalid).
**Fix**: Changed default height from `1` to `160` (reasonable default).

**File Modified**: `MainAct.kt:43`

### 3. **Age WheelView Null Safety** ⚠️ MODERATE
**Issue**: Using `?.let` for age wheel could silently fail if view not found.
**Impact**: Age would remain default (25) without user knowing the input failed.
**Fix**:
- Changed from `?.let` to explicit null check with `if (ageWheelView != null)`
- Added logging fallback when age wheel not found
- Default age = 25 still valid

**File Modified**: `MainAct.kt:167-178`

### 4. **LiveData Lifecycle Issue** ⚠️ MINOR
**Issue**: `lifecycleScope.launch {}` wrapping `.observe()` is incorrect pattern.
**Impact**: Could cause lifecycle leaks or improper observation cleanup.
**Fix**:
- Removed unnecessary `lifecycleScope.launch`
- LiveData.observe() already handles lifecycle automatically
- Now properly cleaned up when Activity destroyed

**File Modified**: `HistoryActivity.kt:89-101`

### 5. **Body Fat Formula Crash** ⚠️ CRITICAL
**Issue**: `log10(waist - neck)` crashes if neck > waist (log10 of negative = NaN).
**Impact**: App crash on invalid measurements, no graceful error handling.
**Fix**:
- Added validation: `if (waistMinusNeck <= 0) return 0.0`
- Added negative result check: `if (result < 0) 0.0 else result`
- Applied same fix for female formula with hip measurement

**File Modified**: `CalculatorUtils.kt:86-113`

### 6. **Negative Ideal Weight** ⚠️ MODERATE
**Issue**: `ideal - 5` could return negative weight for very short people.
**Impact**: Nonsensical negative weight recommendations displayed to user.
**Fix**: Added minimum constraint: `val min = maxOf(ideal - 5, 30.0)` (30kg minimum)

**File Modified**: `CalculatorUtils.kt:65-79`

### 7. **Profile Switch Race Condition** ⚠️ CRITICAL
**Issue**: `setCurrentProfile()` calls two separate database queries without transaction.
**Impact**: Race condition - multiple profiles could be marked as current simultaneously.
**Fix**:
- Created `setCurrentProfileAtomic()` with `@Transaction` annotation
- Ensures both `clearAllCurrent()` and `setCurrentProfile()` execute atomically
- Updated repository to use atomic method

**Files Modified**:
- `ProfileDao.kt:29-33`
- `BmiRepository.kt:52-54`

### 8. **Toolbar Title Overflow On Narrow Screens** ⚠️ MODERATE (found 2026-08-26, TECNO BG6 720px width)
**Issue**: 12 activity toolbars share a copy-pasted title `TextView` pattern with no `maxLines`/
`ellipsize`/autosize — on screens narrower than the design reference (720px physical width, common
budget devices), long titles (esp. Vietnamese, e.g. "Máy Tính BMI") wrap to 2 lines. The toolbar
container (`match_parent`/fixed-height, not truly `wrap_content`-safe with a 2-line child) doesn't
grow to fit, so the second line visually overlaps the screen content below instead of being clipped
or the toolbar growing — real, reproducible UI corruption, not a screenshot artifact.
**Impact**: Title text overlapped page content on `MainAct`, `ResultAct`, and 10 other screens
(Calculators hub, BMR/TDEE/Body-Fat/Ideal-Weight calculators, Settings, History, VIP, Family
Challenge, Progress Photos) on narrower/lower-DPI devices.
**Fix**: Added `android:maxLines="1"` + `android:ellipsize="end"` (safety net) + AppCompat
`app:autoSizeTextType="uniform"` (`autoSizeMinTextSize`/`autoSizeMaxTextSize`/`autoSizeStepGranularity`)
to each toolbar title `TextView` — text now always fits on one line, shrinking automatically instead
of wrapping/overlapping, adapts to any screen width/locale without hardcoding shorter strings.

**Files Modified**: `a_main.xml`, `a_result.xml`, `a_vip.xml`, `a_calculators.xml`,
`a_bmr_calculator.xml`, `a_tdee_calculator.xml`, `a_body_fat_calculator.xml`,
`a_ideal_weight_calculator.xml`, `a_settings.xml`, `a_history.xml`, `a_family_challenge.xml`,
`a_progress_photos.xml`

## ✅ Logic Validation Checks (Already Correct)

### 1. **Input Validation** ✓
- All calculator activities use `toDoubleOrNull()` and `toIntOrNull()`
- Proper null checks before calculations
- User-friendly error messages

### 2. **Zero Division Protection** ✓
```kotlin
fun calculateBMI(weight: Double, height: Double): Double {
    if (height <= 0 || weight <= 0) return 0.0
    return (weight / ((height / 100).pow(2)))
}
```

### 3. **Default Values** ✓
- Weight: 50kg (reasonable)
- Height: 160cm (fixed from 1)
- Age: 25 years (reasonable)
- Gender: Male (M)

### 4. **Database Operations** ✓
- All Room operations on IO dispatcher
- Proper error handling with try-catch
- User feedback with Toast messages

### 5. **Navigation** ✓
- All activities registered in AndroidManifest
- Menu items properly linked to activities
- Back buttons functional

## 🔍 Potential Issues NOT Fixed (Out of Scope)

### 1. **Multi-Profile Switching**
- Current: Only default profile used
- Future: Need UI to create/switch profiles

### 2. **Units System**
- Current: Only metric (kg, cm)
- Future: Add Imperial (lbs, inches) toggle

### 3. **Data Export**
- Current: Data only in local database
- Future: Export to PDF/CSV

### 4. **Chart Performance**
- Current: Loads all records at once
- Future: Pagination for large datasets

### 5. **Offline/Online Sync**
- Current: Local only
- Future: Cloud backup

## 📊 Testing Checklist

✅ App launches successfully
✅ Default profile created on first launch
✅ BMI calculation works with default values
✅ Age picker works (or falls back to default)
✅ Height/Weight pickers update correctly
✅ Result screen shows all insights
✅ Save to history works
✅ History screen loads records
✅ Chart displays when records exist
✅ Delete records works
✅ All calculators validate input
✅ Navigation between screens works
✅ Back buttons work
✅ No crashes on tested flows

## 🚀 Build Status

**Before Fixes**: ✅ BUILD SUCCESSFUL (but with logic bugs)
**After Fixes**: ✅ BUILD SUCCESSFUL (logic bugs fixed)

**Build Command**:
```bash
./gradlew assembleDevDebug
```

**Result**:
```
BUILD SUCCESSFUL in 5s
41 actionable tasks: 41 executed
```

## 📝 Summary

**Total Issues Found**: 7
**Critical**: 3 (Profile ID, Body Fat crash, Race condition)
**Moderate**: 3 (Height default, Age null safety, Negative ideal weight)
**Minor**: 1 (LiveData lifecycle)
**Total Fixed**: 7
**Remaining**: 0 blocking issues

All critical and moderate issues have been resolved. The app now has proper:
- Default value initialization
- Profile management foundation with atomic transactions
- Null safety handling
- Lifecycle-aware data observation
- Complete input validation and edge case handling
- Error handling for calculation formulas
- Transaction safety for database operations

---

**Last Updated**: January 2025
**Status**: ✅ ALL ISSUES FIXED (7 bugs in 2 review rounds)
