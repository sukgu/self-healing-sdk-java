package com.sdk;

import org.openqa.selenium.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.*;
import org.openqa.selenium.NoSuchElementException;

import java.util.*;
import java.util.regex.*;
import java.util.UUID;

// Aspect to intercept Selenium WebDriver.findElement(By) calls
@Aspect
public class SelfHealingSeleniumAspect {

    private final SelfHealingSdk sdk;

    // Cache to avoid duplicate registrations per selector per session
    private final Set<String> registeredSelectors = Collections.synchronizedSet(new HashSet<>());

    public SelfHealingSeleniumAspect() {
        System.out.println("------------------------------------------------- in constructor");
        this.sdk = new SelfHealingSdk("http://localhost:8080"); // Adjust your healing API base URL
    }

    @Around("execution(* org.openqa.selenium.WebDriver.findElement(org.openqa.selenium.By)) && args(by)")
    public Object findElementWithHealingAndRegister(ProceedingJoinPoint pjp, By by) throws Throwable {
        WebDriver driver = (WebDriver) pjp.getTarget();
        String selector = by.toString();

        try {
            // Try normal findElement first
            WebElement element = (WebElement) pjp.proceed();

            // Register the element if not already registered
            if (!registeredSelectors.contains(selector)) {
                try {
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    // Execute JavaScript to get all attributes
                    Object object = js.executeScript("var items = {}; for (index = 0; index < arguments[0].attributes.length; ++index) { items[arguments[0].attributes[index].name] = arguments[0].attributes[index].value }; return items;", element);
                    Map<String, String> attributes = new HashMap<>();
                    if(object instanceof Map) {
                        attributes.putAll((Map<String, String>) object);
                    }

                    String pageUrl = driver.getCurrentUrl();
                    String tagName = element.getTagName();
                    String text = element.getText();
                    String elementOuterHtml = element.getAttribute("outerHTML");

                    attributes.put("page_url", pageUrl);
                    attributes.put("tag_name", tagName);
                    attributes.put("text", text != null ? text : "");
                    attributes.put("outer_html", elementOuterHtml);

                    // Save multiple selectors for healing fallback strategy
                    List<String> selectors = new ArrayList<>();
                    String cssSelector = extractCssSelectorFromBy(by);
                    String xpathSelector = extractXPathSelectorFromBy(by);
                    if ((cssSelector != null) && !cssSelector.isEmpty() && !selectors.contains("css: " + cssSelector)) {
                        selectors.add("css: " + cssSelector);
                    }
                    if ((xpathSelector != null) && !xpathSelector.isEmpty() && !selectors.contains("xpath: " + xpathSelector)) {
                        selectors.add("xpath: " + xpathSelector);
                    }

                    //Here I will implement GenAI to fetch all possible selector for this web element and I will save all the selectors in database

                    SelfHealingSdk.ElementFingerprint fp = new SelfHealingSdk.ElementFingerprint();
                    fp.id = UUID.randomUUID();
                    fp.attributes = attributes;
                    fp.selectors = selectors;

                    SelfHealingSdk.RegisterRequest request = new SelfHealingSdk.RegisterRequest();
                    request.fingerprint = fp;
                    sdk.registerFingerprint(request);
                    registeredSelectors.add(selector);
                } catch (Exception e) {
                    System.err.println("[SelfHealingSeleniumAspect] Registration failed: " + e.fillInStackTrace());
                }
            }

            return element;

        } catch (NoSuchElementException e) {
            // On failure, attempt healing
            SelfHealingSdk.HealRequest healRequest = new SelfHealingSdk.HealRequest();
            healRequest.failed_selector = selector;
            Map<String, String> context = new HashMap<>();
            context.put("page_url", driver.getCurrentUrl());
            healRequest.context = context;

            SelfHealingSdk.HealResponse response;
            try {
                response = sdk.healSelector(healRequest);
            } catch (Exception ex) {
                System.err.println("[SelfHealingSeleniumAspect] Healing API call failed: " + ex.getMessage());
                throw e; //  propagate original exception
            }
            if (response != null && response.healed_selector != null && !response.healed_selector.isEmpty()) {
                By healedBy = parseSelectorString(response.healed_selector);
                if (healedBy != null) {
                    try {
                        WebElement healedElement = driver.findElement(healedBy);

                        System.out.println("[SelfHealingSeleniumAspect] Healed selector used: " + response.healed_selector);

                        // Register healed element if new
                        if (!registeredSelectors.contains(response.healed_selector)) {
                            try {
                                String pageUrl = driver.getCurrentUrl();
                                String tagName = healedElement.getTagName();
                                String text = healedElement.getText();
                                String id = healedElement.getAttribute("id");
                                String name = healedElement.getAttribute("name");
                                String classes = healedElement.getAttribute("class");
                                String ariaLabel = healedElement.getAttribute("aria-label");
                                String placeholder = healedElement.getAttribute("placeholder");
                                String type = healedElement.getAttribute("type");

                                Map<String, String> attributes = new HashMap<>();
                                attributes.put("page_url", pageUrl);
                                attributes.put("tag_name", tagName);
                                attributes.put("text", text != null ? text : "");
                                attributes.put("id", id != null ? id : "");
                                attributes.put("name", name != null ? name : "");
                                attributes.put("class", classes != null ? classes : "");
                                attributes.put("aria-label", ariaLabel != null ? ariaLabel : "");
                                attributes.put("placeholder", placeholder != null ? placeholder : "");
                                attributes.put("type", type != null ? type : "");

                                List<String> selectors = new ArrayList<>();
                                selectors.add(response.healed_selector);

                                SelfHealingSdk.ElementFingerprint fp = new SelfHealingSdk.ElementFingerprint();
                                fp.id = UUID.randomUUID();
                                fp.attributes = attributes;
                                fp.selectors = selectors;

                                SelfHealingSdk.RegisterRequest req = new SelfHealingSdk.RegisterRequest();
                                req.fingerprint = fp;
                                sdk.registerFingerprint(req);

                                registeredSelectors.add(response.healed_selector);
                            } catch (Exception ex) {
                                System.err.println("[SelfHealingSeleniumAspect] Registration of healed selector failed: " + ex.getMessage());
                            }
                        }

                        return healedElement;

                    } catch (NoSuchElementException nse) {
                        System.err.println("[SelfHealingSeleniumAspect] Healing selector did not find element: " + response.healed_selector);
                    }
                }
            }

            // Healing failed or no healed selector, throw original
            throw e;
        }
    }



    // Register element after successful findElement
    //@AfterReturning(pointcut = "execution(* org.openqa.selenium.WebDriver.findElement(org.openqa.selenium.By)) && args(by)", returning = "element")
    public void registerElement(JoinPoint jp, By by, WebElement element) {
        System.out.println("--------------------------------------------------");
        try {
            String selector = by.toString(); // e.g. 'By.id: username' or 'By.cssSelector: .btn'
            if (!registeredSelectors.contains(selector)) {
                WebDriver driver = (WebDriver) jp.getTarget();
                String pageUrl = driver.getCurrentUrl();
                String tagName = element.getTagName();
                String text = element.getText();
                String id = element.getAttribute("id");
                String name = element.getAttribute("name");
                String classes = element.getAttribute("class");
                String ariaLabel = element.getAttribute("aria-label");
                String placeholder = element.getAttribute("placeholder");
                String type = element.getAttribute("type");

                Map<String, String> attributes = new HashMap<>();
                attributes.put("page_url", pageUrl);
                attributes.put("tag_name", tagName);
                attributes.put("text", text != null ? text : "");
                attributes.put("id", id != null ? id : "");
                attributes.put("name", name != null ? name : "");
                attributes.put("class", classes != null ? classes : "");
                attributes.put("aria-label", ariaLabel != null ? ariaLabel : "");
                attributes.put("placeholder", placeholder != null ? placeholder : "");
                attributes.put("type", type != null ? type : "");

                // Save multiple selectors for healing fallback strategy
                List<String> selectors = new ArrayList<>();
                selectors.add(selector);
                // Add alternative selector strings if you want (e.g. stripped versions):
                String cssSelector = extractCssSelectorFromBy(by);
                if (cssSelector != null && !cssSelector.isEmpty() && !selectors.contains("By.cssSelector: " + cssSelector)) {
                    selectors.add("By.cssSelector: " + cssSelector);
                }

                SelfHealingSdk.ElementFingerprint fp = new SelfHealingSdk.ElementFingerprint();
                fp.id = UUID.randomUUID();
                fp.attributes = attributes;
                fp.selectors = selectors;

                SelfHealingSdk.RegisterRequest request = new SelfHealingSdk.RegisterRequest();
                request.fingerprint = fp;
                sdk.registerFingerprint(request);

                registeredSelectors.add(selector);
            }
        } catch (Exception e) {
            System.err.println("[SelfHealingSeleniumAspect] Registration failed: " + e.getMessage());
        }
    }

    // Intercept findElement to do self-healing on NoSuchElementException
    //@Around("execution(* org.openqa.selenium.WebDriver.findElement(org.openqa.selenium.By)) && args(by)")
    public Object findElementWithHealing(ProceedingJoinPoint pjp, By by) throws Throwable {
        System.out.println("-+++++++++++++++++++++++++++++++-");
        WebDriver driver = (WebDriver) pjp.getTarget();
        String selector = by.toString();

        try {
            // Try normal findElement first
            return pjp.proceed();
        } catch (NoSuchElementException e) {
            // Call healing API
            SelfHealingSdk.HealRequest healRequest = new SelfHealingSdk.HealRequest();
            healRequest.failed_selector = selector;
            Map<String, String> context = new HashMap<>();
            context.put("page_url", driver.getCurrentUrl());
            healRequest.context = context;

            SelfHealingSdk.HealResponse response;
            try {
                response = sdk.healSelector(healRequest);
            } catch (Exception ex) {
                System.err.println("[SelfHealingSeleniumAspect] Healing API call failed: " + ex.getMessage());
                throw e; // propagate original exception
            }
            if (response != null && response.healed_selector != null && !response.healed_selector.isEmpty()) {
                By healedBy = parseSelectorString(response.healed_selector);
                if (healedBy != null) {
                    try {
                        WebElement healedElement = driver.findElement(healedBy);
                        System.out.println("[SelfHealingSeleniumAspect] Healed selector used: " + response.healed_selector);
                        return healedElement;
                    } catch (NoSuchElementException nse) {
                        // Healing failed too
                        System.err.println("[SelfHealingSeleniumAspect] Healing selector did not find element: " + response.healed_selector);
                        throw e;
                    }
                }
            }
            // Healing unavailable or no success
            throw e;
        }
    }

    // Helper method: convert By.toString() into By object (basic support for id, cssSelector, xpath)
    private By parseSelectorString(String selectorStr) {
        if (selectorStr == null) return null;

        // Example: "By.id: username"
        Pattern pattern = Pattern.compile("By\\.(\\w+): (.+)");
        java.util.regex.Matcher matcher = pattern.matcher(selectorStr);
        if (matcher.matches()) {
            String type = matcher.group(1);
            String value = matcher.group(2);
            switch (type) {
                case "id":
                    return By.id(value);
                case "cssSelector":
                    return By.cssSelector(value);
                case "xpath":
                    return By.xpath(value);
                case "name":
                    return By.name(value);
                case "className":
                    return By.className(value);
                case "tagName":
                    return By.tagName(value);
                default:
                    System.err.println("[SelfHealingSeleniumAspect] Unsupported selector type for healing: " + type);
                    return null;
            }
        }
        System.err.println("[SelfHealingSeleniumAspect] Cannot parse selector string: " + selectorStr);
        return null;
    }

    // Optional: extract cleaner cssSelector from By string (if possible)
    private String extractCssSelectorFromBy(By by) {
        String s = by.toString();  // e.g., "By.cssSelector: .btn-primary"
        if (s.startsWith("By.cssSelector: ")) {
            return s.substring("By.cssSelector: ".length());
        }
        return null;
    }

    // Optional: extract cleaner xpathSelector from By string (if possible)
    private String extractXPathSelectorFromBy(By by) {
        String s = by.toString();  // e.g., "By.xpath: //button"
        if (s.startsWith("By.xpath: ")) {
            return s.substring("By.xpath: ".length());
        }
        return null;
    }
}

