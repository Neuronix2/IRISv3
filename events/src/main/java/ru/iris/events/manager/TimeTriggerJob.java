/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package ru.iris.events.manager;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.iris.events.types.Event;
import ru.iris.events.types.Rule;
import ru.iris.events.types.TriggerType;

/**
 * QuartzJob for Time-Triggers
 * 
 * @author Simon Merschjohann
 * @since 1.7.0
 */
public class TimeTriggerJob implements Job {

	private static final Logger logger = LoggerFactory.getLogger(TimeTriggerJob.class);
	private Rule rule;

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		String scriptName = ScriptManager.getInstance().getScript(rule).getFileName();
		logger.info("TimeTrigger for rule: " + rule + ", scriptName: " + scriptName);

		ScriptManager manager = ScriptManager.getInstance();

		manager.executeRules(new Rule[] { rule }, new Event(TriggerType.TIMER, null));
	}

	public void setRule(Rule rule) {
		this.rule = rule;
	}

}
