package com.sdk;

import io.github.bonigarcia.wdm.WebDriverManager;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestSelfHealingSdk {

//    public static void main(String[] args) {
//        TestSelfHealingSdk testSelfHealingSdk = new TestSelfHealingSdk();
//        testSelfHealingSdk.testSelfHealingSeleniumAspect();
//
//    }
    @Test
    public void testSelfHealingSeleniumAspect() {

        WebDriverManager.chromedriver().setup();
        WebDriver driver = new ChromeDriver();
        driver.get("https://linkedin.com");
        WebElement webElement = driver.findElement(By.xpath("//a[contains(text(), \"Sign in\")]"));
        if(webElement.isDisplayed()) {
            System.out.println("qqqqqqqqqqqqqqqqqqqqqqqqqq");
        }
        driver.quit();
    }

    public void testSelfhealingSdk() {
        try {
            SelfHealingSdk sdk = new SelfHealingSdk("http://localhost:8080");

            // Create heal request
            SelfHealingSdk.HealRequest healReq = new SelfHealingSdk.HealRequest();
            healReq.failed_selector = ".btn-save";
            healReq.context = Map.of("page_url", "https://example.com", "text", "Save");

            SelfHealingSdk.HealResponse healResp = sdk.healSelector(healReq);

            System.out.println("Healed Selector: " + healResp.healed_selector);
            System.out.println("Confidence: " + healResp.confidence);

            // Register an element fingerprint
            SelfHealingSdk.ElementFingerprint fp = new SelfHealingSdk.ElementFingerprint();
            fp.id = UUID.randomUUID();
            fp.attributes = Map.of("page_url", "https://example.com", "text", "Save Button");
            fp.selectors = List.of(".btn-save", ".save-button");

            SelfHealingSdk.RegisterRequest regReq = new SelfHealingSdk.RegisterRequest();
            regReq.fingerprint = fp;

            sdk.registerFingerprint(regReq);

            // List all registered fingerprints
            var all = sdk.getAllFingerprints();
            System.out.println("Registered fingerprints count: " + all.size());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
