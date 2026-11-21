package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import utils.BaseTest;

public class TestLoginKo extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("blabla", "somepassword");
		assertTrue(ip.getVersion().contains("Dolibarr 3.9.0-beta"));
	}
}
