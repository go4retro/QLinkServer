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
	Created on Jul 25, 2005
	
 */
package org.jbrain.qlink;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;


import org.apache.log4j.Logger;
import org.jbrain.qlink.cmd.action.Action;
import org.jbrain.qlink.cmd.action.SendOLM;
import org.jbrain.qlink.cmd.action.SendSYSOLM;
import org.jbrain.qlink.connection.*;
import org.jbrain.qlink.db.DBUtils;
import org.jbrain.qlink.state.*;

public class QServer {
	private static Logger _log=Logger.getLogger(QServer.class);

	/**
	 * 
	 * @uml.property name="_link"
	 * @uml.associationEnd multiplicity="(1 1)"
	 */
	private QConnection _link;

	/**
	 * 
	 * @uml.property name="_state"
	 * @uml.associationEnd multiplicity="(1 1)"
	 */
	private QState _state;

	private String _sHandle=null;
	private static Hashtable _htServers=new Hashtable();
	private Connection _conn=null;
	private int _iAccountID;
	private int _iUserID;
	private Date _startTime;

	/**
	 * 
	 * @uml.property name="_htOLMTable"
	 * @uml.associationEnd qualifier="id:java.lang.String java.lang.String" multiplicity=
	 * "(0 1)"
	 */
	private Hashtable _htOLMTable = new Hashtable();

	private int _iOLMID=0;

	/**
	 * 
	 * @uml.property name="_linklistener"
	 * @uml.associationEnd multiplicity="(1 1)"
	 */
	private ConnEventListener _linklistener = new ConnEventListener() {
		public void actionOccurred(ActionEvent event) {
			try {
				_log.debug(_state.getName()
					+ ": Executing "
					+ event.getAction().getName());
				_state.execute(event.getAction());
			} catch (IOException e) {
				// this means the connection died, so close down the server.
				_log.error("Link error detected, shutting down instance", e);
				terminate();
			} catch (RuntimeException e) {
				_log.error(
					"Runtime error encountered, shutting down instance",
					e);
				terminate();
			}
		}
	};

	private boolean _bOLMs;
	public static final String MESSAGE_SYSTEM = "SYS";
	public static final String MESSAGE_NORMAL = "OLM";
	

	
	public QServer(QConnection link) {
		_link=link;
		_state=new Authentication(this);
		_link.addEventListener(_linklistener);
		// start receiving data
		_link.start();
		_startTime=new Date();
		
	}
	
	public void send(Action a) throws IOException {
		_log.debug("Dispatching Action: " + a.getName());
		_link.send(a);
	}
	/**
	 * @param update
	 */
	public void setState(QState state) {
		_log.debug("Setting state to: " + state.getName());
		_state=state;
	}
	
	public synchronized void setHandle(String handle) {
		_log.debug("User handle: " + handle);
		_log.info("Adding '" + handle + "' to online user list");
		_htServers.put(handle.toLowerCase(),this);
		_sHandle=handle;
	}
	
	/**
	 * @return
	 */
	public String getHandle() {
		return _sHandle;
	}
	
	/**
	 * @param int1
	 */
	public void setAccountID(int i) {
		_log.debug("Account ID: " + i);
		_iAccountID=i;
	}
	
	public int getAccountID() {
		return _iAccountID;
	}

	public void setUserID(int i) {
		_log.debug("Account ID: " + i);
		_iUserID=i;
	}
	
	public int getUserID() {
		return _iUserID;
	}

	/**
	 * @param textActions
	 */
	public void send(Action[] actions) throws IOException {
		if(actions != null) {
			for(int i=0;i<actions.length;i++) {
				send(actions[i]);
			}
		}
	}

	/**
	 * @return
	 */
	public QState getState() {
		return _state;
	}

	/**
	 * 
	 */
	public synchronized void terminate() {
		_log.info("Terminating server instance");
		try {
			_link.removeEventListener(_linklistener);
			_state.terminate();
			_link.close();
		} catch (RuntimeException e) {
			_log.error("Encountered unchecked Exception",e);
		} finally {
			// remove from map.
			if(getHandle()!=null) {
				_log.info("Removing '" + getHandle() + "' from online user list");
				_htServers.remove(getHandle().toLowerCase());
			}
		}
	}

	/**
	 * @return
	 */
	public Date getStartTime() {
		return _startTime;
	}

	/**
	 * @param recipient
	 * @return
	 */
	public int getIDByName(String handle) {
        Connection conn;
        Statement stmt = null;
        ResultSet rs = null;
        int rc=-1;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Attemping to find Account ID for '" + handle + "'");
        	rs=stmt.executeQuery("SELECT account_id from accounts WHERE handle='" + handle + "'");
        	if(rs.next()) {
        		rc=rs.getInt("account_id");
        	} else {
            	_log.debug("'" + handle + "' not a valid user");
        	}
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        } finally {
        	if(rs!=null) {
        		try { rs.close(); 
        		} catch (Exception e) {}
        	}
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) { }// ignore }

                stmt = null;
            }
        }
        return rc;
	}
	
	public int getNextID(int start, int type, int max) {
        Connection conn;
        Statement stmt = null;
        ResultSet rs = null;
        String sql; 
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Attemping to find next available message base ID after "+ start);
        	int orig_id=start;
        	do {
        		start++;
        		if(start>max)
        			start=0;
        		sql="SELECT reference_id from entry_types where reference_id=" + start;
        		_log.debug(sql);
        		rs=stmt.executeQuery(sql);
        	} while(rs.next() && start!=orig_id);
    		try { rs.close(); 
    		} catch (Exception e) {}
        	if(start==orig_id) {
        		// error
        		_log.error("Cannot find ID <=" + max);
        		return -1;
        	} else {
        		_log.debug("Creating new entry_types record");
    			sql="insert into entry_types (reference_id,entry_type) VALUES (" + start + "," + type + ")";
				_log.debug(sql);
    			stmt.execute(sql);
    			if(stmt.getUpdateCount()==0) {
    				_log.error("Could not insert record into entry_types");
    				return -1;
    			}
        	}
    		return start;
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	return -1;
        } finally {
        	if(rs!=null) {
        		try { rs.close(); 
        		} catch (Exception e) {}
        	}
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) { }// ignore }

                stmt = null;
            }
        }
	}
	

	/**
	 * @param toID
	 * @param ew
	 */
	public boolean sendToUser(String handle, Action a) {
		QServer s=getSession(handle);
		_log.debug("Attempting to send action to another user: " + handle);
		if(s!=null) {
			try {
				s.send(a);
				return true;
			} catch (IOException e) {
				// this means the connection died, so close down the server.
				_log.error("Link error detected, shutting down instance",e);
				s.terminate();
			}
		}
		return false;
	}

	/**
	 * @return
	 */
	public boolean isUserOnline(String handle) {
		return getSession(handle) != null;
	}

	/**
	 * @param recipient
	 * @param objects
	 */
	public void sendOLM(String recipient, String[] olm) {
		QServer s=getSession(recipient);
		if(s!= null && s.canReceiveOLMs()) {
			s.sendOLM(false,olm);
		}
	}

	/**
	 * @param olm
	 */
	public void sendOLM(boolean bType,String[] olm) {
		String id;
		_log.debug("Preparing OLM for user:" + getHandle());
		synchronized(_htOLMTable) {
			//if(_htOLMTable.size()==0)
			//	_iOLMID=0;
			DecimalFormat sdf=new DecimalFormat("0000000");
			if(bType)
				id= MESSAGE_SYSTEM + sdf.format(_iOLMID++);
			else
				id= MESSAGE_NORMAL + sdf.format(_iOLMID++);
			_htOLMTable.put(id,olm);
		}
		try {
			// send ONOLM command.
			send(new SendOLM(id));
		} catch (IOException e) {
			// this means the connection died, so close down the server.
			_log.error("Link error detected, shutting down instance",e);
			terminate();
		}
	}
	
	public String[] getOLM(String id) {
		return (String[])_htOLMTable.remove(id);
	}
	
	public void enableOLMs(boolean state) {
		_bOLMs=state;
	}
	
	public boolean canReceiveOLMs(String user) {
		QServer server=getSession(user);
		return (server != null && server.canReceiveOLMs());
	}

	/**
	 * @return
	 */
	private boolean canReceiveOLMs() {
		return _bOLMs;
	}

	/**
	 * @param user
	 * @return
	 */
	private static QServer getSession(String name) {
		return (QServer)_htServers.get(name.toLowerCase());
	}
	
	public void suspend() {
		_link.suspendLink();
	}
	
	public void resume() {
		_link.resumeLink();
	}

	/**
	 * @param msg
	 */
	public static void sendSYSOLM(String msg) {
		QServer s;
		
		synchronized(_htServers) {
			Iterator i=_htServers.values().iterator();
			while(i.hasNext()) {
				s=(QServer)i.next();
				try {
					s.send(new SendSYSOLM(msg));
				} catch (IOException e) {
					s.terminate();
				}
			}
			
		}
	}

	/**
	 * @param name
	 */
	public static boolean killSession(String name) {
		QServer s=getSession(name);
		if(s!=null) {
			s.terminate();
			return true;
		}
		return false;
	}
}
