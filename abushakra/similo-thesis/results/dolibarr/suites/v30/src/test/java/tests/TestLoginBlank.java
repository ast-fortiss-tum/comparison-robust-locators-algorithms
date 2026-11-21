package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import utils.BaseTest;

public class TestLoginBlank extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "");

		assertTrue(ip.getVersion().contains("Dolibarr 3.9.0-beta"));
		assertTrue(ip.getError().contains("Bad value"));
	}
}
