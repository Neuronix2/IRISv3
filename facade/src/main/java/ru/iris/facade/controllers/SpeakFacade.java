package ru.iris.facade.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.bus.EventBus;
import ru.iris.commons.helpers.SpeakHelper;
import ru.iris.commons.service.Speak;
import ru.iris.facade.model.SpeakRequest;
import ru.iris.facade.model.status.ErrorStatus;
import ru.iris.facade.model.status.OkStatus;

@RestController
@Profile("facade")
public class SpeakFacade {

	private final SpeakHelper helper;

	@Autowired
	public SpeakFacade(
	    SpeakHelper helper
	)
	{
		this.helper = helper;
	}

	/**
	 * Say something on specified zone (or at all zones)
	 *
	 * @param request request
	 * @return ok or error status
	 */
	@RequestMapping(value = "/api/speak", method = RequestMethod.POST)
	public Object sayAtZone(@RequestBody SpeakRequest request) {

		if(request.getText() != null && !request.getText().isEmpty()) {
			helper.say(request.getText());
		}
		else {
			new ErrorStatus("empty text passed");
		}

		return new OkStatus("Saying: " + request.getText());
	}
}
