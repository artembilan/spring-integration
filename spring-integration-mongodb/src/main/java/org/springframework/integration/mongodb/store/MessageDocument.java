/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.mongodb.store;

import java.util.UUID;

import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

/**
 * The entity class to wrap {@link org.springframework.messaging.Message} to the MongoDB document.
 *
 * @author Artem Bilan
 * @since 4.0
 */
@Document
@AccessType(AccessType.Type.PROPERTY)
public class MessageDocument {

	/*
	 * Needed as a persistence property to suppress 'Cannot determine IsNewStrategy' MappingException
	 * when the application context is configured with auditing. The document is not
	 * currently Auditable.
	 */
	@Id
	private String _id;

	private final Message<?> message;

	private final UUID messageId;

	private Integer priority;

	private Long createdTime = 0L;

	private Object groupId;

	private Long lastModifiedTime = 0L;

	private Boolean complete = false;

	private Integer lastReleasedSequence = 0;

	private int sequence;

	public MessageDocument(Message<?> message) {
		Assert.notNull(message, "'message' must not be null");
		this.message = message;
		this.messageId = message.getHeaders().getId();
	}

	public Message<?> getMessage() {
		return this.message;
	}

	public UUID getMessageId() {
		return this.messageId;
	}

	public void setGroupId(Object groupId) {
		this.groupId = groupId;
	}

	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	public Long getLastModifiedTime() {
		return this.lastModifiedTime;
	}

	public void setLastModifiedTime(long lastModifiedTime) {
		this.lastModifiedTime = lastModifiedTime;
	}

	public Long getCreatedTime() {
		return this.createdTime;
	}

	public void setCreatedTime(long createdTime) {
		this.createdTime = createdTime;
	}

	public Boolean isComplete() {
		return this.complete;
	}

	public void setComplete(boolean complete) {
		this.complete = complete;
	}

	public Integer getLastReleasedSequence() {
		return this.lastReleasedSequence;
	}

	public void setLastReleasedSequence(int lastReleasedSequence) {
		this.lastReleasedSequence = lastReleasedSequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public Integer getPriority() {
		return this.priority;
	}

	public Object getGroupId() {
		return this.groupId;
	}

	public int getSequence() {
		return this.sequence;
	}

}
