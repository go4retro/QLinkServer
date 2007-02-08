/*
	Copyright Jim Brain and Brain Innovations, 2005.

	This file is part of QLinkServer.

	QLinkServer is free software; you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation; either version 2 of the License, or
	(at your option) any later version.

	QLinkServer is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with QLinkServer; if not, write to the Free Software
	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

	@author Jim Brain
	Created on Jul 23, 2005
	
 */
package org.jbrain.qlink.state;

import java.io.*;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.jbrain.qlink.*;
import org.jbrain.qlink.cmd.action.*;

public class SendOLMState extends AbstractState {
	private static Logger _log=Logger.getLogger(SendOLMState.class);

	/**
	 * 
	 * @uml.property name="_intState"
	 * @uml.associationEnd multiplicity="(0 1)"
	 */
	private QState _intState;

	private String _sRecipient;

	/**
	 * 
	 * @uml.property name="_alOLMText"
	 * @uml.associationEnd elementType="java.lang.String" multiplicity="(0 -1)"
	 */
	private ArrayList _alOLMText = new ArrayList();

	
	public SendOLMState(QServer server, String recipient) {
		super(server);
		_sRecipient=recipient;
}
	
	public void activate() throws IOException {
		_log.debug("User requested to send an OLM to " + _sRecipient);
		//if(_server.getHandle().toLowerCase().equals(_sRecipient)) {
			//_log.debug("User trying to send OLM to him/herself");
			//_server.send(new SendSYSOLM("You cannot send an OLM to yourself"));
		//} else
		if(_server.getIDByName(_sRecipient)==-1) {
			_log.debug("OLM Error: No such user");
			_server.send(new UserInvalid());
		} else if(!_server.isUserOnline(_sRecipient)) {
			_log.debug("OLM Error: User is not online");
			_server.send(new UserNotOnline());
		} else if(_server.canReceiveOLMs(_sRecipient)) {
			_intState=_server.getState();
			super.activate();
			_log.debug("User can receive OLMs");
			_server.send(new SendOLMAck(_server.getHandle()));
		} else {
			_log.debug("OLM Error: User cannot receive OLMs");
			_server.send(new SendOLMNAck());
		}
	}
	

	public boolean execute(Action a) throws IOException {
		boolean rc=false;
		if(a instanceof OM) {
			// save first/next line of olm text;
			String text=((OM)a).getData();
			_log.debug("OLM Text: " + text);
			_alOLMText.add(text);
			rc=true;
		} else if(a instanceof OE) {
			// save last line of OLM text
			String text=((OE)a).getData();
			_log.debug("OLM End: " + text);
			_alOLMText.add(text);
			// now, send to other user.
			_server.sendOLM(_sRecipient,(String[])_alOLMText.toArray(new String[0]));
			// restore state
			_server.setState(_intState);
			rc=true;
		} else if(a instanceof OLMCancelled) {
			// email is cancelled
			_log.debug("Cancelled OLM to " + _sRecipient);
			_server.setState(_intState);
		} else {
			rc=_intState.execute(a);
		}
		return rc;
	}


	public void terminate() {
		_intState.terminate();
	}
}
