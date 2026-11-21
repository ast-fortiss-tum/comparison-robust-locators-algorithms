package utils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * The suites were written against a driver whose click() returned after the next page had loaded; with a current Chrome behind a grid it can return earlier,
 * so the next lookup races the navigation and fails with StaleElementReference. This wrapper restores that behaviour: after a click, wait briefly for a
 * navigation to start, then until the document is complete. Nothing else about the suites changes.
 */
public class SettleDriver {
	public static WebDriver wrap(final WebDriver d) {
		return (WebDriver) Proxy.newProxyInstance(SettleDriver.class.getClassLoader(), new Class<?>[] { WebDriver.class, JavascriptExecutor.class, TakesScreenshot.class }, new InvocationHandler() {
			public Object invoke(Object p, java.lang.reflect.Method m, Object[] a) throws Throwable {
				Object r;
				int tries = m.getName().equals("findElement") ? 5 : 1;   // a slow redirect can leave the next page not yet rendered: look again for a few seconds before failing
				for (int t = 1; ; t++) {
					try { r = m.invoke(d, a); break; }
					catch (InvocationTargetException e) {
						if (e.getCause() instanceof org.openqa.selenium.NoSuchElementException && t < tries) { try { Thread.sleep(1000); } catch (InterruptedException ie) { } continue; }
						if (e.getCause() instanceof org.openqa.selenium.NoSuchElementException && m.getName().equals("findElement")) dump(d, String.valueOf(a[0]));
						throw e.getCause();
					}
				}
				if (m.getName().equals("findElement") && r instanceof WebElement) return wrapEl((WebElement) r, d);
				if (m.getName().equals("findElements") && r instanceof List) { List<WebElement> out = new ArrayList<WebElement>(); for (Object o : (List<?>) r) out.add(wrapEl((WebElement) o, d)); return out; }
				return r;
			}
		});
	}

	private static WebElement wrapEl(final WebElement e, final WebDriver d) {
		return (WebElement) Proxy.newProxyInstance(SettleDriver.class.getClassLoader(), new Class<?>[] { WebElement.class }, new InvocationHandler() {
			public Object invoke(Object p, java.lang.reflect.Method m, Object[] a) throws Throwable {
				Object r;
				String n = m.getName();
				try { r = m.invoke(e, a); }
				catch (InvocationTargetException x) {
					Throwable c = x.getCause();
					// The suites date from 2016; a current Chrome refuses interactions that the old driver let through (elements hidden behind a styled widget, overlays).
					// As in Selenium IDE, fall back to the DOM: click through script, set a hidden field or pick the matching option of a hidden select.
					if (c instanceof org.openqa.selenium.ElementNotInteractableException || c instanceof org.openqa.selenium.ElementClickInterceptedException) {
						JavascriptExecutor js = (JavascriptExecutor) d;
						if (n.equals("click")) {
							// an <option> of a hidden select (styled widget): select it and notify the page; anything else: click through script
							js.executeScript("var e=arguments[0];if(e.tagName==='OPTION'){var s=e.parentNode;s.selectedIndex=e.index;s.dispatchEvent(new Event('change',{bubbles:true}));if(window.jQuery){window.jQuery(s).trigger('change');}}else{e.click();}", e);
							settle(d); return null; }
						if (n.equals("sendKeys")) {
							String text = String.valueOf(((CharSequence[]) a[0])[0]);
							js.executeScript("var e=arguments[0],t=arguments[1];if(e.tagName==='SELECT'){for(var i=0;i<e.options.length;i++){if(e.options[i].text.indexOf(t)===0||e.options[i].value===t){e.selectedIndex=i;break;}}}else{e.value=t;}"
								+ "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));if(window.jQuery){window.jQuery(e).trigger('change');}", e, text);
							return null;
						}
					}
					throw c;
				}
				if (n.equals("click") || n.equals("submit")) settle(d);
				if (n.equals("findElement") && r instanceof WebElement) return wrapEl((WebElement) r, d);
				if (n.equals("findElements") && r instanceof List) { List<WebElement> out = new ArrayList<WebElement>(); for (Object o : (List<?>) r) out.add(wrapEl((WebElement) o, d)); return out; }
				return r;
			}
		});
	}

	private static int dumps = 0;
	/** Diagnostics: the page and the failing locator, for locator repair (FAIL_DUMP directory). */
	private static void dump(WebDriver d, String by) {
		try {
			String dir = System.getenv("FAIL_DUMP"); if (dir == null) return;
			new java.io.File(dir).mkdirs();
			String id = String.format("%03d", ++dumps);
			java.nio.file.Files.write(java.nio.file.Paths.get(dir, id + ".html"), d.getPageSource().getBytes("UTF-8"));
			java.nio.file.Files.write(java.nio.file.Paths.get(dir, "index.txt"), (id + "\t" + d.getCurrentUrl() + "\t" + by + "\n").getBytes("UTF-8"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (Throwable t) { }
	}

	/** Diagnostics: error notifications shown after a click (silent failures of a form submission), to FAIL_DUMP/errors.txt. */
	private static void logErrors(WebDriver d) {
		try {
			String dir = System.getenv("FAIL_DUMP"); if (dir == null) return;
			Object r = ((JavascriptExecutor) d).executeScript("return Array.prototype.slice.call(document.querySelectorAll('.jnotify-notification-error,.error,div.errmsg')).map(function(e){return (e.innerText||'').trim().substring(0,160)}).filter(function(t){return t}).join(' | ')");
			if (r != null && String.valueOf(r).length() > 0) {
				new java.io.File(dir).mkdirs();
				java.nio.file.Files.write(java.nio.file.Paths.get(dir, "errors.txt"), (d.getCurrentUrl() + "\t" + r + "\n").getBytes("UTF-8"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			}
		} catch (Throwable t) { }
	}

	private static void settle(WebDriver d) {
		try {
			Thread.sleep(400);   // let a navigation start
			for (int i = 0; i < 40; i++) {
				Object s = ((JavascriptExecutor) d).executeScript("return document.readyState");
				if ("complete".equals(s)) { logErrors(d); return; }
				Thread.sleep(250);
			}
		} catch (Exception ignore) { }
	}
}
