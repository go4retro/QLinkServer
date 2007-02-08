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

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.apache.log4j.Logger;
import org.jbrain.qlink.QServer;
import org.jbrain.qlink.cmd.action.*;
import org.jbrain.qlink.db.DBUtils;


public abstract class AbstractState implements QState {
	private static Logger _log=Logger.getLogger(AbstractState.class);

	/**
	 * 
	 * @uml.property name="_server"
	 * @uml.associationEnd multiplicity="(1 1)"
	 */
	//public static final int PHASE_INITIAL=1;
	protected QServer _server;

	public AbstractState(QServer server) {
		_server=server;
	}
	
	public void activate() throws IOException {
		_server.setState(this);
	}
	
	public void passivate() throws IOException {
	}
	
	public boolean execute(Action a) throws IOException {
		QState state;
		boolean rc=false;
		
		if(a instanceof LostConnection) {
			_server.terminate();
			rc=true;
		} else if(a instanceof SuspendServiceAck) {
			_log.debug("Sending ACK for Suspend Service Request");
			_server.send(new SuspendServiceAck());
			_server.suspend();
			rc=true;
		} else if(a instanceof ResumeService) {
			_log.debug("Resuming service");
			_server.resume();
			//_log.debug("Sending ACK for Resume Service Request");
			//_server.send(new ResumeService());
			rc=true;
		} else if(a instanceof Logoff) {
			_log.debug("Sending ACK for Logoff Request");
			_server.send(new LogoffAck(_server.getStartTime(),new Date()));
			_server.terminate();
			rc=true;
		} else if(a instanceof SendEmail) {
			String recipient=((SendEmail)a).getData();
			state=new SendEmailState(_server, recipient);
			state.activate();
			rc=true;
		} else if(a instanceof ReadEmail) {
			state=new ReadEmailState(_server);
			state.activate();
			rc=true;
		} else if(a instanceof SendOLM) {
			String recipient=((SendOLM)a).getData();
			state=new SendOLMState(_server,recipient);
			state.activate();
			rc=true;
		} else if(a instanceof ReadOLM) {
			String id=((ReadOLM)a).getData();
			boolean bSysMsg=id.startsWith(QServer.MESSAGE_SYSTEM);
			// system OKs reading an OLM
			String[] l=_server.getOLM(id);
			int size=l.length;
			for(int i=0;i<size;i++) {
				_server.send(new OLMText(id,l[i],bSysMsg && (i+1==size)));
			}
			if(!bSysMsg)
				_server.send(new OLMText(id,"End of Message - Press F5 to cancel",true));
			rc=true;
		}
		return rc;
	}

	/**
	 * @return
	 */
	protected boolean checkEmail() {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Checking for email to " + _server.getHandle());
            rs=stmt.executeQuery("SELECT email_id FROM email WHERE unread='Y' AND recipient_id=" + _server.getID() + " LIMIT 1");
            return rs.next();
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	return false;
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
	}

	public void terminate() {
	}
	
	public String getName() {
		return this.getClass().getName();
	}

	
}
