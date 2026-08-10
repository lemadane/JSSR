package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

class PackagingTest {

    @Test
    @DisplayName("Library core must not package application.properties into production classpath")
    void libraryMustNotPackageApplicationPropertiesInMainResources() {
        File mainProps = new File("src/main/resources/application.properties");
        assertFalse(mainProps.exists(), "src/main/resources/application.properties must not exist in core library source tree");
    }
}
