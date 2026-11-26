<?php
// Fixture seed: replacement for the authors' (lost) test.php that the Selenese suites open right after a fresh install.
// Inserts the sample content the suites browse and edit (news, articles, categories, downloads, web links, shouts, submissions, a second member).
require_once "config.php";
$l = mysql_connect($db_host, $db_user, $db_pass); mysql_select_db($db_name, $l);
$p = $db_prefix; $now = time();
function q($sql) { global $l; if (!mysql_query($sql, $l)) echo "ERR: ".mysql_error($l)." :: $sql\n"; }
// idempotent: the suites open this script more than once
if (mysql_num_rows(mysql_query("SELECT 1 FROM {$p}article_cats WHERE article_cat_name='Article name cat 2'", $l)) > 0) { echo "already seeded\n"; exit; }
// members
q("INSERT INTO {$p}users (user_name,user_password,user_email,user_hide_email,user_location,user_birthdate,user_aim,user_icq,user_msn,user_yahoo,user_web,user_theme,user_offset,user_avatar,user_sig,user_posts,user_joined,user_lastvisit,user_ip,user_rights,user_groups,user_level,user_status) VALUES ('Yasmine','".md5('Maths2069')."','yasmine@example.com','1','','0000-00-00','','','','','','Default','0','','',0,$now,0,'0.0.0.0','','',101,0)");
// news (subjects edited by the admin News test, 'Read More' via extended text)
foreach (array('First subject','Second subject','Third subject','Fourth subject') as $i => $s)
  q("INSERT INTO {$p}news (news_subject,news_cat,news_news,news_extended,news_breaks,news_name,news_datestamp,news_start,news_end,news_visibility,news_reads,news_allow_comments,news_allow_ratings) VALUES ('$s',0,'Short text of $s','Extended text of $s','y',1,".($now-$i*60).",0,0,0,0,1,1)");
// articles
q("INSERT INTO {$p}article_cats (article_cat_name,article_cat_description,article_cat_sorting,article_cat_access) VALUES ('Article name cat 2','second article category','article_subject ASC',0)");
$cat = mysql_insert_id($l);
foreach (array('Article name cat 3','Article name cat 4','Article name cat 5') as $s) q("INSERT INTO {$p}article_cats (article_cat_name,article_cat_description,article_cat_sorting,article_cat_access) VALUES ('$s','$s description','article_subject ASC',0)");
foreach (array('Parker','Test 3','Test 4','Test 5') as $i => $s)
  q("INSERT INTO {$p}articles (article_cat,article_subject,article_snippet,article_article,article_breaks,article_name,article_datestamp,article_reads,article_allow_comments,article_allow_ratings) VALUES ($cat,'$s','Snippet of $s','Text of $s','y',1,".($now-$i*60).",0,1,1)");
$a3 = mysql_result(mysql_query("SELECT article_id FROM {$p}articles WHERE article_subject='Test 3'", $l), 0);
$smcol = mysql_num_rows(mysql_query("SHOW COLUMNS FROM {$p}comments LIKE 'comment_smileys'", $l)) > 0;   // column removed in 7.x
for ($c = 1; $c <= 3; $c++) q("INSERT INTO {$p}comments (comment_item_id,comment_type,comment_name,comment_message,".($smcol ? "comment_smileys," : "")."comment_datestamp,comment_ip) VALUES ($a3,'A','1','comment $c',".($smcol ? "'1'," : "")."$now,'0.0.0.0')");
// downloads and web links
foreach (array('Category 1','Category 2','Category 3') as $s) q("INSERT INTO {$p}download_cats (download_cat_name,download_cat_description,download_cat_sorting,download_cat_access) VALUES ('$s','$s description','download_title ASC',0)");
q("INSERT INTO {$p}weblink_cats (weblink_cat_name,weblink_cat_description,weblink_cat_sorting,weblink_cat_access) VALUES ('weblink name','web link category','weblink_name ASC',0)");
// shouts, submissions
foreach (array('first shout','second shout','third shout') as $i => $m) q("INSERT INTO {$p}shoutbox (shout_name,shout_message,shout_datestamp,shout_ip) VALUES (1,'$m',".($now-$i*30).",'0.0.0.0')");
// pending submissions (admin Submissions test publishes/deletes 4 links, 2 news and 3 articles)
function sub($type, $arr, $age) { global $p, $now; q("INSERT INTO {$p}submissions (submit_type,submit_user,submit_datestamp,submit_criteria) VALUES ('$type',2,".($now-$age).",'".mysql_real_escape_string(serialize($arr))."')"); }
for ($i = 1; $i <= 4; $i++) sub('l', array('link_category'=>'1','link_name'=>"Submitted link $i",'link_url'=>"http://example.com/$i",'link_description'=>"submitted link $i"), $i);
for ($i = 1; $i <= 2; $i++) sub('n', array('news_cat'=>'0','news_subject'=>"Submitted news $i",'news_body'=>"submitted news text $i",'news_breaks'=>'y'), $i);
for ($i = 1; $i <= 3; $i++) sub('a', array('article_cat'=>'1','article_subject'=>"Submitted article $i",'article_snippet'=>"snippet $i",'article_body'=>"submitted article text $i",'article_breaks'=>'y'), $i);
echo "seed done\n";
