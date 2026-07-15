@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared::vo",
        "member::api",
        "sale::api",
        "inventory::api",
        "payment::api"
    }
)
package com.example.limitedticketing.purchase;
