---
description: Reference guide for BDD testing with Cucumber + Compose on Android
globs: ["mobile/androidApp/src/androidTest/**/*.kt", "spec/**/*.feature"]
alwaysApply: false
---

# BDD with Cucumber + Compose on Android

Hard-earned gotchas and fixes for running Cucumber-Android 7.18.1 with Jetpack Compose instrumented tests.

## Cucumber-Android Infrastructure

### @CucumberOptions annotation is mandatory

cucumber-android 7.18.1 requires a class annotated with `@io.cucumber.junit.CucumberOptions` in the test APK. Without it you get `CucumberOptionsAnnotationMissing` at runtime.

**Fix:** Create a `CucumberTestSuite.kt`:

```kotlin
@CucumberOptions(/* features, glue, etc. */)
class CucumberTestSuite
```

### optionsAnnotationPackage for flavor builds

If the test APK package differs from where the annotated class lives (e.g., a `demo` flavor appends `.demo` to the applicationId), Cucumber cannot find the annotation.

**Fix:** In `build.gradle.kts`:

```kotlin
testInstrumentationRunnerArguments["optionsAnnotationPackage"] = "id.cachet.wallet.android.bdd"
```

### @WithJunitRule is required for Compose rules

cucumber-android's `RulesBackend` ONLY discovers JUnit rules on classes annotated with `@io.cucumber.junit.WithJunitRule`. Without it, `@Rule` fields are silently ignored and Compose test rules never activate. This is the #1 gotcha.

**Fix:** Annotate every step definition class that declares rules:

```kotlin
@WithJunitRule
class MySteps {
    @Rule @JvmField
    val composeTestRule = createAndroidComposeRule<MainActivity>()
}
```

### @JvmField not @get:Rule

Cucumber's rule discovery expects a plain field, not a Kotlin property getter.

**Fix:** Always use `@Rule @JvmField val ...`, never `@get:Rule val ...`.

### Duplicate step definitions across annotations

Cucumber treats `@Given`, `@When`, `@Then` identically for matching. The same pattern string on two different annotations is a duplicate, causing a runtime error.

**Fix:** Use one annotation per pattern. If a step is used as both Given and Then, define it once with `@Given` (or whichever) and Cucumber will match it for all annotations.

### {word} only matches a single word

`{word}` captures one word. Multi-word values like "Age Verification" need a different parameter type.

**Fix:** Use `{string}` (requires quotes in the feature file) or `{}` (anonymous, matches anything) for multi-word values.

## Compose Testing in Cucumber Steps

### ViewModel survives Activity.recreate()

`ActivityScenario.recreate()` simulates a config change. ViewModel survives, so stale state persists.

**Fix:** Either add a `reloadDemoData()` method triggered by `LaunchedEffect`, or make ViewModel properties check runtime flags like `DemoFixtures.isDemoActive` on each access.

### Demo mode cannot use Intent extras

The Compose test rule auto-launches the activity before step definitions run. Intent extras set in steps arrive too late.

**Fix:** Use a global flag (`DemoFixtures.isDemoActive`) checked by the Application class and ViewModels. Set it in `@Before` or Given steps, clear it in `@After`.

### Multiple matching nodes

`onNodeWithText("text").assertIsDisplayed()` fails if multiple nodes match.

**Fix:** Use `onAllNodesWithText("text").onFirst().assertIsDisplayed()`.

### assertIsDisplayed vs assertExists

`assertIsDisplayed()` requires the node to be in the viewport. Items in `LazyColumn`/`LazyVerticalGrid` that are off-screen will fail.

**Fix:** Use `assertExists()` for items that may be scrolled out of view. Reserve `assertIsDisplayed()` for items guaranteed to be visible.

### performScrollTo before performClick

Buttons at the bottom of a scrollable container may be off-screen and not clickable.

**Fix:** Call `performScrollTo()` before `performClick()`. Wrap in `try/catch(Throwable)` because it throws `AssertionError` (not `Exception`) when the node is not in a scrollable container:

```kotlin
try { node.performScrollTo() } catch (_: Throwable) {}
node.performClick()
```

### Back button is an icon, not text

Navigation back buttons use `contentDescription`, not visible text.

**Fix:** Use `onNodeWithContentDescription("Back")` instead of `onNodeWithText("Back")`.

### waitUntil for async transitions

`Thread.sleep()` is flaky for async UI transitions (e.g., QR scan to result screen).

**Fix:** Use `rule.waitUntil(timeoutMillis = 5000) { condition }` for deterministic waits.

### Camera permission crashes instrumented tests

`rememberLauncherForActivityResult(RequestPermission)` launches a system dialog that crashes instrumented tests.

**Fix:** Skip the permission request in demo mode:

```kotlin
if (!demoMode && !hasCameraPermission) {
    permissionLauncher.launch(Manifest.permission.CAMERA)
}
```

### Demo QR code URL must not trigger real network calls

`cachet://` URLs trigger the real relay network flow, which fails in tests.

**Fix:** Use a non-relay prefix like `demo://` that hits the demo fallback path directly.

### Onboarding persistence for multi-launch tests

Testing "second launch skips onboarding" requires onboarding completion to be persisted.

**Fix:** Persist onboarding state in SharedPreferences. Clear prefs in `@After` so each scenario starts fresh.

### Database writes may fail in demo mode

Consent receipt writes and other DB operations may fail if the database is not fully initialized in instrumented tests.

**Fix:** Wrap database writes in `try/catch` in demo mode.

## Step Definition Patterns

### Idempotent Given/Then steps

When the same pattern (e.g., "I am on screen X") is used as both `@Given` (navigate there) and `@Then` (assert you are there), the method must handle both cases.

**Fix:** Check if already on the target screen. If yes, assert and return. If no, navigate.

### Navigation steps should navigate, not just assert

A step like `Given I am on the Incoming Request screen` must navigate to that screen if the app is not already there.

**Fix:** Implement navigation logic in Given steps, not just `assertIsDisplayed()`.

### @After cleanup is mandatory

Global state leaks between scenarios if not cleaned up.

**Fix:** Always reset in `@After`:

```kotlin
@After
fun cleanup() {
    DemoFixtures.isDemoActive = false
    DemoFixtures.activeScenario = DemoScenario.HAPPY
    // Clear SharedPreferences
}
```
