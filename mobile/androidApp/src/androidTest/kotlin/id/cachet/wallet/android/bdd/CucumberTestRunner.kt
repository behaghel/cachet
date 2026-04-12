package id.cachet.wallet.android.bdd

import io.cucumber.android.runner.CucumberAndroidJUnitRunner

/**
 * Custom Cucumber test runner for BDD scenarios.
 *
 * Feature files are loaded from androidTest/assets/features/.
 * Step definitions are in the id.cachet.wallet.android.bdd.steps package.
 */
class CucumberTestRunner : CucumberAndroidJUnitRunner()
