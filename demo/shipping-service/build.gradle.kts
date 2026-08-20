dependencies {
    // Real cycle: orders-service -> shipping-service -> orders-service
    implementation(project(":orders-service"))
    implementation(project(":common-utils"))
}
