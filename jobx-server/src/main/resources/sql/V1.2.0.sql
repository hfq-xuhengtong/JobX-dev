
--t_agent
create table `t_agent` (
  `agent_id` bigint(20) not null auto_increment,
  `comment` text,
  `email` text,
  `host` varchar(255),
  `machine_id` varchar(64),
  `mobile` text,
  `name` varchar(50),
  `notify_time` datetime,
  `password` varchar(50),
  `port` int(10),
  `proxy_id` bigint(20),
  `status` tinyint(1),
  `update_time` datetime,
  `warning` bit(1),
  primary key (`agent_id`)
) engine=innodb auto_increment=10000 default charset=utf8;


--t_agent_group
create table `t_agent_group` (
  `group_id` bigint(20) not null,
  `agent_id` bigint(20) not null,
  primary key (`group_id`,`agent_id`),
  key qa_agentid (`agent_id`)
) engine=innodb default charset=utf8;

--t_config
create table `t_config` (
  `config_key` varchar(50),
  `config_val` text,
  `comment` varchar(255),
  primary key (`config_key`)
) engine=innodb default charset=utf8;

--t_group
create table `t_group` (
   `group_id` bigint(20) not null auto_increment,
   `comment` text,
   `create_time` datetime,
   `group_name` varchar(255),
   `user_id` bigint(20),
  primary key (`group_id`)
) engine=innodb auto_increment=10000 default charset=utf8;

--t_job
create table `t_job` (
  `job_id` bigint(20) not null auto_increment,
  `agent_id` bigint(20),
  `user_id` bigint(20),
  `job_type` tinyint(1),
  `job_name` varchar(50),
  `command` text,
  `comment` text,
  `cron_exp` varchar(255),
  `cron_type` tinyint(1),
  `warning` bit(1),
  `email` text,
  `mobile` text,
  `pause` bit(1),
  `redo` bit(1),
  `run_count` int(10),
  `success_exit` varchar(255),
  `timeout` int(10),
  `token` varchar(64),
  `update_time` datetime,
  `create_type` tinyint(1),
  primary key (`job_id`),
  key qa_agent_id (`agent_id`)
) engine=innodb auto_increment=10000 default charset=utf8;

--t_log
create table `t_log` (
  `log_id` bigint(20) not null auto_increment,
  `agent_id` bigint(20),
  `user_id` bigint(20),
  `is_read` bit(1),
  `message` text,
  `receiver` varchar(255),
  `result` text,
  `send_time` datetime,
  `type` tinyint(1),
  primary key (`log_id`)
) engine=innodb auto_increment=10000 default charset=utf8;

--t_record
create table `t_record` (
  `record_id` bigint(20) not null auto_increment,
  `agent_id` bigint(20),
  `command` text,
  `start_time` datetime,
  `end_time` datetime,
  `exec_type` tinyint(1),
  `group_id` bigint(20),
  `user_id` bigint(20),
  `job_id` bigint(20),
  `job_type` tinyint(1),
  `parent_id` bigint(20),
  `pid` varchar(64),
  `redo_num` int(10),
  `return_code` int(10),
  `status` int(10),
  `success` int(10),
  primary key (`record_id`),
  key qa_success (`success`)
) engine=innodb auto_increment=10000 default charset=utf8;

--t_record_message
create table `t_record_message` (
  `record_id` bigint(20) not null,
  `message` longtext,
  `start_time` datetime,
  primary key (`record_id`)
) engine=innodb default charset=utf8;

--t_role
create table `t_role` (
  `role_id` bigint(20) not null,
  `role_name` varchar(255),
  `description` varchar(255),
  primary key (`role_id`)
) engine=innodb default charset=utf8;

--t_terminal
create table `t_terminal` (
  `id` bigint(20) not null auto_increment,
  `host` varchar(255),
  `user_name` varchar(20),
  `authorization` blob,
  `login_time` datetime,
  `name` varchar(255),
  `passphrase` blob,
  `port` int(10) not null,
  `private_key` blob,
  `ssh_type` int(10),
  `status` varchar(20),
  `theme` varchar(20),
  `user_id` bigint(20),
  primary key (`id`)
) engine=innodb auto_increment=10000 default charset=utf8;

--t_user
create table `t_user` (
  `user_id` bigint(20) not null auto_increment,
  `contact` varchar(50),
  `create_time` datetime,
  `email` varchar(50),
  `header_pic` longblob,
  `modify_time` datetime,
  `password` varchar(50),
  `pic_ext_name` varchar(10),
  `qq` varchar(20),
  `real_name` varchar(50),
  `role_id` bigint(20),
  `salt` varchar(64),
  `user_name` varchar(50),
  primary key (`user_id`)
) engine=innodb auto_increment=10000 default charset=utf8;

--t_user_agent
create table `t_user_agent` (
  `id` bigint(20) not null auto_increment,
  `user_id` int(11),
  `agent_id` int(11),
  primary key (`id`)
) engine=innodb auto_increment=10000 default charset=utf8;


-------------------------------- init data ----------------------------------------
--init config
insert into `t_config`(`config_key`,`config_val`,`comment`)
values
("sender_email",null,"发送邮箱的账号"),
("email_password",null,"发送邮箱的密码"),
("smtp_host",null,"发送邮箱的smtp"),
("smtp_port",null,"发送邮箱的port"),
("send_url",null,"短信发送通道商url"),
("space_time","30","发送告警时间间隔"),
("template",null,"发送短信的模板"),
("version","V1.2.0","当前jobx版本号");

--init role
insert into `t_role`(`role_id`, `role_name`, `description`)
values (1,"admin","普通管理员"),
(999,"superadmin","超级管理员");

--init user
insert into `t_user`(`user_name`,`password`,`salt`,`role_id`,`real_name`,`create_time`)
values ("jobx","5e5d211101a00750152fb667503f3c46b5e5c866","9a08523e66d84a17",999,"JobX",now());
