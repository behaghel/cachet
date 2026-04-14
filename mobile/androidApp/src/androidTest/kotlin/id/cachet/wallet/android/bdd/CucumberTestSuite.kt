package id.cachet.wallet.android.bdd

import io.cucumber.junit.CucumberOptions

@CucumberOptions(
    features = ["features"],
    glue = ["id.cachet.wallet.android.bdd.steps"]
)
class CucumberTestSuite
