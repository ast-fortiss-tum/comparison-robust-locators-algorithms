# AddressBook: releases whose own suite does not pass, and why

Replay of the study's suites (ICST16/AddressBook, 75 releases with a matching zip) against the Dockerised release (php:5.6, MySQL 5.6, sql_mode empty, PHP display_errors off).
Final replay: 60 releases pass every test, 15 fail, none unstable. Decision: every failure is counted as an application bug; nothing was patched.

| Releases | Failing tests | Cause | Status |
|---|---|---|---|
| 5.2 to 5.4.6 (9 releases) | EditGroup, InvalidEditGroup, Non-SelectedEditGroup, ViewGroup, RemoveContactFromGroup | Stray `</form>` after the parent-group `<select>` in the group edit form: the Update button ends up outside the form and nothing is submitted | confirmed |
| 3.3.9 | EditGroup, ViewGroup, RemoveContactFromGroup, DeleteGroup | `include/dbconnect.php` hard-codes the table `addr_group_list`, the install creates `group_list`: no group is ever listed | confirmed |
| 4.1.1 | SearchNonExistent | "Select all" ticks rows hidden by the live search filter, so the expected `No address selected.` alert never appears (4.1.3 selects only visible items) | confirmed |
| 6.1, 6.1.1 | ViewMap, PrintContact, DeleteContact, DeleteOneField, DeleteAllBlank | History code runs `INSERT ... SELECT ... FROM addressbook` without WHERE: duplicate primary key (`Duplicate entry '1-0000-00-00 00:00:00'`) after the second edit and the contact disappears; fixed in 6.1.2 | confirmed |
| 6.2.1 | ViewGroup, RemoveContactFromGroup, ViewMap | `include/dbconnect.php` lacks the `$_REQUEST` import loop that 6.2.0 and 6.2.2 have: group forms are saved blank | group tests confirmed, ViewMap not isolated |
| 5.5 | ViewGroup, RemoveContactFromGroup | The group rename never runs (no `UPDATE group_list` is issued); cause not isolated | unexplained |
| 3.3.0 | Languages (excluded) | The suite file itself contains U+FFFD in several locators (e.g. `img[alt="ž�語"]`), so the test cannot pass; it aborts with the UI in Chinese and broke 7 later tests. Replayed with the Languages test skipped (38/38 pass); original data kept in addrbook-work/out_orig_final/3.3.0 | suite fault |

Replay semantics (Selenium IDE): click works on hidden elements (JS fallback), `type` fires key events, `sendKeys` supported, 3 s wait for alerts, short pause after submits, retries when finding elements. 5.7.3 ships `$disp_cols` commented out in its config: enabled in the config only. 6.2 to 6.2.9 ship an active `$userlist` entry (login required): commented out in the config.
