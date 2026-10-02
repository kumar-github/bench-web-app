package com.example.benchmatch;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import com.vaadin.flow.theme.Theme;

/**
 * Entry point. Serves both the Vaadin Flow UI and the REST API (controller package, under /api/**) from one Spring Boot
 * deployable, per the 2026-09-27 architecture decision in demand-supply-mapping-requirements.md — Vaadin views call the
 * service layer in-process; REST controllers expose the same service layer for any consumer outside the browser.
 */
@SpringBootApplication
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css") // Your custom styles
@Push
public class BenchMatchApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(BenchMatchApplication.class, args);
    }
}
