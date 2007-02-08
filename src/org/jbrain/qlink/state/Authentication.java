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
import java.util.*;

import org.apache.log4j.Logger;
import org.jbrain.qlink.*;
import org.jbrain.qlink.cmd.action.*;
import org.jbrain.qlink.db.DBUtils;
import org.jbrain.qlink.dialog.*;


public class Authentication extends AbstractPhaseState {
	private static Logger _log=Logger.getLogger(Authentication.class);
	public static final int PHASE_INITIAL=1;
	public static final int PHASE_UPDATECODE=2;
	protected static final int PHASE_BULLETIN = 3;
	private static Random _random = new Random();
	private static EntryDialog _newUserDialog;
	private static InfoDialog _welcomeDialog;

	private DialogCallBack _newUserCallBack = new DialogCallBack() {
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
					_server.send(_newUserDialog
						.getErrorResponse("We're sorry, but you must select a screen name"));
				} else {
					// clean off handle.
					_handle=_handle.trim();
					// uppercase first char
					_handle=_handle.substring(0,1).toUpperCase() + _handle.substring(1);
					if (_handle.length() > 10) {
						_log.debug("Handle '" + _handle + "' is too long");
						_server.send(_newUserDialog
							.getErrorResponse("We're sorry, but '"
								+ _handle
								+ "' is too long.  Please select a shorter name"));
					} else if (_handle.length() < 3) {
							_log.debug("Handle '" + _handle + "' is too short");
							_server.send(_newUserDialog
								.getErrorResponse("We're sorry, but '"
									+ _handle
									+ "' is too short.  Please select a longer name."));
					} else if (containsInvalidChars(_handle)) {
						_log.debug("'" + _handle + "' contains invalid characters");
						_server
							.send(_newUserDialog
								.getErrorResponse("We're sorry, but screen names can only contains letters, digits, or spaces.  Please select another name."));
					} else if (!Character.isLetter(_handle.charAt(0))) {
						_log.debug("'"
							+ _handle
							+ "' contains leading space or number");
						_server
							.send(_newUserDialog
								.getErrorResponse("We're sorry, but screen names must start with a letter.  Please select another name."));
					} else {
						try {
							if (containsReservedWords(_handle)) {
								_log.debug("'"
									+ _handle
									+ "' contains a reserved word");
								_server
									.send(_newUserDialog
										.getErrorResponse("We're sorry, but your choice contains a reserved word.  Please select another name."));
							} else {
								// adding name.
								if (addUser()) {
									_server.setHandle(_handle);
									_server
										.send(((EntryDialog) d)
											.getSuccessResponse("Congratulations, "
												+ _handle
												+ "!\n\nWe hope you enjoy your visit to Q-LINK."));
									return true;
								} else {
									_server
										.send(_newUserDialog
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
				_server.send(new AddPrimaryAccount(_account, _handle));
				_server.send(new ClearExtraAccounts());
				_server.send(new ChangeAccessCode(_code));
				_log.info("PHASE: Updating access code on disk");
				_server.setState(Authentication.this);
				setPhase(PHASE_UPDATECODE);
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

	static {
		// define a static dialog for this.
		_log.debug("Defining NEWUSER dialog");
		_newUserDialog=new EntryDialog("NEWUSER", true, EntryDialog.FORMAT_NONE);
		_newUserDialog.addText("           WELCOME TO Q-LINK!\n\nBefore we begin, please choose a screen name and type it in.  This name will be used to identify you in the system.  Your screen name can be up to 10 characters in length.");
	}
	
	public Authentication(QServer server) {
		super(server,PHASE_INITIAL);
	}

	public boolean execute(Action a) throws IOException {
		QState state;
		boolean rc=false;
		
        // handle global stuff here
        switch(getPhase()) {
        	case PHASE_INITIAL:
        		if(a instanceof Login) {
        			_account=((Login)a).getAccount();
        			_code=((Login)a).getCode();
        			if(_log.isInfoEnabled()) {
        				_log.info("Account: " + _account + " presents access code: '" + _code + "' for validation");
        			}
        			validateUser();
        			rc=true;
        		}
        		break;
        	case PHASE_UPDATECODE:
        		if(a instanceof UpdatedCode) {
        			updateCode();
        			state=new MainMenu(_server);
        			state.activate();
        			rc=true;
        		}
        		break;
        }
        if(!rc)
        	rc=super.execute(a);
        return rc;
	}
	
	protected void updateCode() throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        int id;
        
        if(_log.isDebugEnabled())
        	_log.debug("Updating account '" + _account + "' access code to: " + _code);
        try {
        	conn=DBUtils.getConnection();
            stmt = conn.createStatement();
            if(stmt.execute("UPDATE users set access_code='" + _code + "' WHERE user_id=" + _iUserID)) {
    			_log.info("PHASE: Updating access code on disk");
    			// we did it.
            }
        } catch (SQLException e) {
        	// big time error, send back error string and close connection
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) { }// ignore }
                stmt = null;
            }
            if(conn!=null) 
            	try {
            		conn.close();
            	} catch (SQLException e) {	}
        }
        	
		
	}

	protected void validateUser() throws IOException {
        Connection conn=null;
        Statement stmt = null;
        ResultSet rs = null;
        long id=-1;
        
        try {
        	try {
        		id=Long.parseLong(_account);
            	conn=DBUtils.getConnection();
                stmt = conn.createStatement();
                rs=stmt.executeQuery("SELECT users.user_id,users.access_code,users.active,accounts.active,accounts.handle from accounts,users WHERE accounts.user_id=users.user_id AND accounts.account_id=" + id);
        	} catch (NumberFormatException e) {
            	_log.info("Account code '" + _account + "' not a number, new user");
        	}
            if(id>-1 && rs.next()) {
            	// possible valid account...
            	if(rs.getString("users.access_code").equals(_code)) {
	            	// valid account
	            	// is account valid?
	            	if(rs.getString("accounts.active").equals("Y") && rs.getString("users.active").equals("Y")) {
	            		_handle=rs.getString("accounts.handle");
	            		if(!_server.isUserOnline(_handle)) {
		            		// active account
		                	// set Screen name
		                	_server.setHandle(_handle);
		                	_server.setAccountID((int)id);
		                	_iUserID=rs.getInt("users.user_id");
		                	_server.setUserID(_iUserID);
		                	// update access time information in DB
		                	// if these fail, we're not too worried
		                    stmt.execute("UPDATE users set last_access=now() WHERE user_id=" + _iUserID);
		                    stmt.execute("UPDATE accounts set last_access=now() WHERE account_id=" + id);
		                    
		                    // get new code
		                    // TODO enable this at appropriate time.
		                    //_code=getNewCode();
	
		                    // temp to clean up acounts:
	            			DBUtils.close(rs);
		                    rs=stmt.executeQuery("SELECT account_id from accounts_fix where account_id=" + id);
		                    if(!rs.next()) {
		                    	_log.debug("Fixing account: '" + id + "'");
			    				_server.send(new AddPrimaryAccount(_account, _handle));
			    				_server.send(new ClearExtraAccounts());
			                    stmt.execute("insert into accounts_fix (account_id) VALUES (" + id + ")");
		                    }
		                	// update access code.
		    	    		_server.send(new ChangeAccessCode(_code));
		    				_log.info("PHASE: Updating access code on disk");
		    	    		setPhase(PHASE_UPDATECODE);
	            		} else {
	            			// account is already logged in.
	            			_log.info(_handle + " is already logged in");
	            			_server.send(new DuplicateLogin());
	            		}
	            	} else {
	            		// deal with inactive account....
	            		_log.info("Account '" + _account +"' is inactive");
            			_server.send(new InvalidatedAccount());
	            	}
            	} else {
            		// good account, bad code...
            		_log.info("Account '" + _account +"' failed access code check");
            		_server.send(new InvalidAccount());
            	}
            } else {
            	// if id>-1, then we should really send back DK command.
            	// new user.
            	_log.info("Account not found.  New user");
            	_log.info("Starting new user screen name dialog");
            	_server.send(new ClearScreen());
            	EntryDialogState state=new EntryDialogState(_server,_newUserDialog,_newUserCallBack);
            	state.activate();
            }
        } catch (SQLException e) {
        	_log.error("SQL Exception",e);
        	// big time error, send back error string and close connection
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException sqlEx) {} // ignore }

                rs = null;
            }

            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) { }// ignore }
                stmt = null;
            }
            if(conn!=null) 
            	try {
            		conn.close();
            	} catch (SQLException e) {	}
        }
	}
	
	private boolean addUser() throws IOException {
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
            	// good handle, insert user record.
            	_log.debug("Adding new user");
            	DBUtils.close(rs);
            	String code=getNewCode();
                stmt.execute("INSERT INTO users (access_code,active,create_date,last_access,last_update,orig_account,orig_code) VALUES ('" + code + "','Y',now(),now(),now(),'" + _account + "','" + _code + "')");
                if(stmt.getUpdateCount()>0) {
                	// we added it.
                	// update security code
                	_code=code;
                	rs=stmt.executeQuery("SELECT user_id from users WHERE orig_account='" + _account + "' AND access_code='" + _code + "'");
                	if(rs.next()) {
                		_iUserID=rs.getInt("user_id");
                		DBUtils.close(rs);
                        stmt.execute("INSERT INTO accounts (user_id,active,handle,create_date,last_access,last_update) VALUES (" + _iUserID + ",'Y','" + _handle + "',now(),now(),now())");
                        if(stmt.getUpdateCount()>0) {
                        	// get account number.
                        	rs=stmt.executeQuery("SELECT account_id from accounts WHERE user_id=" + _iUserID);
                        	if(rs.next()) {
                        		_server.setAccountID(rs.getInt("account_id"));
                        		DecimalFormat format=new DecimalFormat("0000000000");
                        		_account=format.format(rs.getInt("account_id"));
                        		rc=true;
                            	_log.debug("New user added");
                        		// we inserted both... We are done.
                        		//Send D4ACCOUNTNUMSCREENNAME
                        	} else {
                        		// db error
                        		_log.error("Count not retrive new account id");
                        	}
                        } else {
                        	// db error...
                        	_log.error("Could not insert record into accounts table");
                        }
                	} else {
                		// db error
                		_log.error("Count not retrieve user id");
                	}
                } else {
                	// db error, could not insert first part.
                	_log.error("Could not insert record into users table");
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

	private String getNewCode() {
		DecimalFormat format=new DecimalFormat("0000");
		return format.format(_random.nextInt(10000));
	}

}
