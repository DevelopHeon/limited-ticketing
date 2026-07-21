@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared::vo",
        "shared::exception",
        "purchase::api",
        "inventory::api"
    }
)
package com.example.limitedticketing.payment;
