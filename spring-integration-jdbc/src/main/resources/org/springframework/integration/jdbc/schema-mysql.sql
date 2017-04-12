-- Autogenerated: do not edit this file

CREATE TABLE INT_MESSAGE  (
	MESSAGE_ID CHAR(36),
	REGION VARCHAR(100),
	CREATED_DATE DATETIME NOT NULL,
	MESSAGE_BYTES BLOB,
	constraint MESSAGE_PK primary key (MESSAGE_ID, REGION)
) ENGINE=InnoDB;

CREATE INDEX INT_MESSAGE_IX1 ON INT_MESSAGE (CREATED_DATE);

CREATE TABLE INT_GROUP_TO_MESSAGE  (
	GROUP_KEY CHAR(36),
	MESSAGE_ID CHAR(36),
	REGION VARCHAR(100),
	constraint GROUP_TO_MESSAGE_PK primary key (GROUP_KEY, MESSAGE_ID, REGION)
) ENGINE=InnoDB;

CREATE TABLE INT_MESSAGE_GROUP  (
	GROUP_KEY CHAR(36),
	REGION VARCHAR(100),
	MARKED BIGINT,
	COMPLETE BIGINT,
	LAST_RELEASED_SEQUENCE BIGINT,
	CREATED_DATE DATETIME NOT NULL,
	UPDATED_DATE DATETIME DEFAULT NULL,
	constraint MESSAGE_GROUP_PK primary key (GROUP_KEY, REGION)
) ENGINE=InnoDB;

CREATE TABLE INT_LOCK  (
	LOCK_KEY CHAR(36),
	REGION VARCHAR(100),
	CLIENT_ID CHAR(36),
	CREATED_DATE DATETIME NOT NULL,
	constraint LOCK_PK primary key (LOCK_KEY, REGION)
) ENGINE=InnoDB;



CREATE TABLE INT_CHANNEL_MESSAGE (
	MESSAGE_ID CHAR(36) NOT NULL,
	GROUP_KEY CHAR(36) NOT NULL,
	CREATED_DATE BIGINT NOT NULL,
	MESSAGE_PRIORITY BIGINT,
	MESSAGE_SEQUENCE BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
	MESSAGE_BYTES BLOB,
	REGION VARCHAR(100) NOT NULL,
	constraint INT_CHANNEL_MESSAGE_PK primary key (GROUP_KEY, MESSAGE_ID, REGION)
) ENGINE=InnoDB;

CREATE INDEX INT_CHANNEL_MSG_DATE_IDX ON INT_CHANNEL_MESSAGE (CREATED_DATE, MESSAGE_SEQUENCE);
CREATE INDEX INT_CHANNEL_MSG_PRIORITY_IDX ON INT_CHANNEL_MESSAGE (MESSAGE_PRIORITY DESC, CREATED_DATE, MESSAGE_SEQUENCE);
