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
import java.text.DecimalFormat;

import org.apache.log4j.Logger;
import org.jbrain.qlink.*;
import org.jbrain.qlink.cmd.action.*;
import org.jbrain.qlink.db.DBUtils;
import org.jbrain.qlink.dialog.*;


public class JimState extends AbstractPhaseState {
	private static Logger _log=Logger.getLogger(JimState.class);
	public static final int PHASE_INITIAL=1;
	private static EntryDialog _addAccountDialog;

	private DialogCallBack _addAccountCallBack = new DialogCallBack() {
		/* (non-Javadoc)
		 * @see org.jbrain.qlink.state.DialogCallBack#handleResponse(org.jbrain.qlink.dialog.AbstractDialog, org.jbrain.qlink.cmd.action.Action)
		 */
		public boolean handleResponse(AbstractDialog d, Action a)
		throws IOException {
		_log.debug("We received " + a.getName() + " from entry dialog");
		if (a instanceof ZA) {
			_handle = ((ZA) a).getResponse();
			if(_handle==null || _handle.length()==0) {
				_log.debug("Handle is null");
				_server.send(_addAccountDialog
					.getErrorResponse("We're sorry, but you must select a screen name"));
			} else {
				// clean off handle.
				_handle=_handle.trim();
				// uppercase first char
				_handle=_handle.substring(0,1).toUpperCase() + _handle.substring(1);
				if (_handle.length() > 10) {
					_log.debug("Handle '" + _handle + "' is too long");
					_server.send(_addAccountDialog
						.getErrorResponse("We're sorry, but '"
							+ _handle
							+ "' is too long.  Please select a shorter name"));
				} else if (_handle.length() < 3) {
						_log.debug("Handle '" + _handle + "' is too short");
						_server.send(_addAccountDialog
							.getErrorResponse("We're sorry, but '"
								+ _handle
								+ "' is too short.  Please select a longer name."));
				} else if (containsInvalidChars(_handle)) {
					_log.debug("'" + _handle + "' contains invalid characters");
					_server
						.send(_addAccountDialog
							.getErrorResponse("We're sorry, but screen names can only contains letters, digits, or spaces.  Please select another name."));
				} else if (!Character.isLetter(_handle.charAt(0))) {
					_log.debug("'"
						+ _handle
						+ "' contains leading space or number");
					_server
						.send(_addAccountDialog
							.getErrorResponse("We're sorry, but screen names must start with a letter.  Please select another name."));
				} else {
					try {
						if (containsReservedWords(_handle)) {
							_log.debug("'"
								+ _handle
								+ "' contains a reserved word");
							_server
								.send(_addAccountDialog
									.getErrorResponse("We're sorry, but your choice contains a reserved word.  Please select another name."));
						} else {
							// adding name.
							if (addAccount()) {
								_server
									.send(((EntryDialog) d)
										.getSuccessResponse("Congratulations, "
											+ _handle
											+ "!\n\nWe hope you enjoy your visit to Q-LINK."));
								return true;
							} else {
								_server
									.send(_addAccountDialog
										.getErrorResponse("We're sorry, but '"
											+ _handle
											+ "' is already in use.  Please select another name."));
							}
						}
					} catch (SQLException e) {
						// something very bad happened... We cannot continue.
						_log.error("Error during reserved word lookup", e);
						_server.terminate();
					}
				}
			}
		} else if (a instanceof D2) {
			_log.debug("Adding new account to disk");
			_server.send(new AddSubAccount(_account, _handle));
			_server.send(new AddAccountInSlot(1));
			return true;
		}
		return false;
	}

	private boolean containsReservedWords(String handle)
		throws SQLException {
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;

		try {
			_log.debug("Checking for reserved words");
			conn = DBUtils.getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT name from reserved_names");
			if (rs.next()) {
				// check the found names
				do {
					if (handle.toLowerCase().indexOf(
						rs.getString("name").toLowerCase()) > -1)
						return true;
				} while (rs.next());
			}
		} finally {
			DBUtils.close(rs);
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException sqlEx) {
				}// ignore }
				stmt = null;
			}
			if (conn != null)
				try {
					conn.close();
				} catch (SQLException e) {
				}
		}
		return false;
	}

	private boolean containsInvalidChars(String handle) {
		boolean rc = false;
		char ch;
		for (int i = 0; i < handle.length(); i++) {
			ch = handle.charAt(i);
			if (!(Character.isLetterOrDigit(ch) || ch == ' '))
				return true;
		}
		return false;
	}
};

	private String _account;
    private String _code;
	private int _iUserID;
	private String _handle;
	private QState _intState;

	static {
		// define a static dialog for this.
		_log.debug("Defining ADDNAME dialog");
		_addAccountDialog=new EntryDialog("ADDNAME", true, EntryDialog.FORMAT_NONE);
		_addAccountDialog.addText("Please choose a screen name and type it in.  Your screen name can be up to 10 characters in length.\n\n.");
	}
	
	public JimState(QServer server) {
		super(server,PHASE_INITIAL);
	}
	
	public void activate() throws IOException {
		_intState=_server.getState();
		super.activate();
    	EntryDialogState state=new EntryDialogState(_server,_addAccountDialog,_addAccountCallBack);
    	state.activate();
	}

	public boolean execute(Action a) throws IOException {
		QState state;
		boolean rc=false;
		
        // handle global stuff here
        switch(getPhase()) {
        	case PHASE_INITIAL:
        		break;
        }
        if(!rc)
        	rc=super.execute(a);
        return rc;
	}
	
	private boolean addAccount() throws IOException {
		boolean rc=false;
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            _log.debug("Checking for duplicate handle");
            rs=stmt.executeQuery("SELECT accounts.account_id FROM accounts WHERE accounts.handle = '" + _handle + "'");
            if(rs.next()) {
            	// someone using this handle, try another.
            	DBUtils.close(rs);
            	_log.info("Handle '" + _handle + "' already in use");
            } else {
            	// good handle, insert new account record.
            	_log.debug("Adding new account");
            	DBUtils.close(rs);
                stmt.execute("INSERT INTO accounts (user_id,active,handle,create_date,last_access,last_update) VALUES (" + _server.getUserID() + ",'Y','" + _handle + "',now(),now(),now())");
                if(stmt.getUpdateCount()>0) {
                	// get account number.
                	rs=stmt.executeQuery("SELECT account_id from accounts WHERE user_id=" + _server.getUserID());
                	if(rs.next()) {
                		DecimalFormat format=new DecimalFormat("0000000000");
                		_account=format.format(rs.getInt("account_id"));
                		rc=true;
                    	_log.debug("New acccount added");
                	} else {
                		// db error
                		_log.error("Count not retrive new account id");
                	}
                } else {
                	// db error...
                	_log.error("Could not insert record into accounts table");
                }
            }
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// big time error, send back error string and close connection
        } finally {
        	DBUtils.close(rs);
        	DBUtils.close(stmt);
        	DBUtils.close(conn);
        }
        return rc;
		
	}
	

}
