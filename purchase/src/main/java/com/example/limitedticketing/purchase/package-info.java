@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared::vo",
        "shared::exception",
        "shared::api",
        "member::api",
        "sale::api",
        "inventory::api"
    }
)
package com.example.limitedticketing.purchase;
