@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared::api",
        "shared::exception",
        "member::api",
        "sale::api",
        "purchase::api"
    }
)
package com.example.limitedticketing.purchasewaiting;
