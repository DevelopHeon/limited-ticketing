@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared::vo",
        "shared::exception",
        "shared::api",
        "purchase::api",
        "inventory::api"
    }
)
package com.example.limitedticketing.payment;
