@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared::vo",
        "shared::exception",
        "member::api",
        "sale::api",
        "inventory::api"
    }
)
package com.example.limitedticketing.purchase;
