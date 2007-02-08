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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jbrain.qlink.*;
import org.jbrain.qlink.chat.*;
import org.jbrain.qlink.cmd.action.*;
import org.jbrain.qlink.text.TextFormatter;

public class Chat extends AbstractState {
	private static Logger _log=Logger.getLogger(Chat.class);
	private Room _room;
	private int _iSeat;
	private QueuedChatEventListener _listener;
	private RoomInfo[] _rooms;
	private int _roomPos;
	private ArrayList _alQuestion=new ArrayList();
	
	class QueuedChatEventListener implements RoomEventListener {
		private boolean _bSuspend=true;
		private ArrayList _alQueue=new ArrayList();

		/* (non-Javadoc)
		 * @see org.jbrain.qlink.chat.BasicRoomEventListener#eventOccurred(org.jbrain.qlink.chat.RoomEvent)
		 */
		public synchronized void suspend() {
			_bSuspend=true;
		}
		
		public synchronized void resume() {
			RoomEvent e;
			
			_bSuspend=false;
			while(_alQueue.size()>0) {
				e=(RoomEvent)_alQueue.remove(0);
				if(e instanceof MessageEvent)
					userSaid((MessageEvent)e);
				else if(e instanceof ChatEvent)
					userSaid((ChatEvent)e);
				else if(e instanceof SystemMessageEvent)
					systemSent((SystemMessageEvent)e);
				else if(e instanceof JoinEvent && ((JoinEvent)e).getType()==JoinEvent.EVENT_JOIN)
					userJoined((JoinEvent)e);
				else if(e instanceof JoinEvent && ((JoinEvent)e).getType()==JoinEvent.EVENT_LEAVE)
					userLeft((JoinEvent)e);
				else if(e instanceof QuestionStateEvent && ((QuestionStateEvent)e).getType()==QuestionStateEvent.ACCEPTING_QUESTIONS)
					acceptingQuestions((QuestionStateEvent)e);
				else if(e instanceof QuestionStateEvent && ((QuestionStateEvent)e).getType()==QuestionStateEvent.NOT_ACCEPTING_QUESTIONS)
					rejectingQuestions((QuestionStateEvent)e);
			}
		}
		public void userSaid(ChatEvent event) {
			if(_bSuspend)
				_alQueue.add(event);
			else
				if(event.isSeated()) {
					if(_log.isDebugEnabled())
						_log.debug("Seat: " + event.getOriginatingSeat() + " says: '" + event.getText() +"'");
					try {
						_server.send(new ChatSend(event.getOriginatingSeat(),event.getText()));
					} catch (IOException e) {
						// leave chat, and shut down.
						_log.error("Link error",e);
						_server.terminate();
					}
				} else {
					if(_log.isDebugEnabled())
						_log.debug("User '" + event.getOriginatingName() + "' says: '" + event.getText() +"'");
					try {
						_server.send(new AnonChatSend(event.getOriginatingName(),event.getText()));
					} catch (IOException e) {
						// leave chat, and shut down.
						_log.error("Link error",e);
						_server.terminate();
					}
				}
			
		}

		/* (non-Javadoc)
		 * @see org.jbrain.qlink.chat.RoomEventListener#userSaid(org.jbrain.qlink.chat.MessageEvent)
		 */
		public void userSaid(MessageEvent event) {
			String error=null;
			//this is always from us, so just send it as an OLM.
			if(_bSuspend)
				_alQueue.add(event);
			else {
				if(_log.isDebugEnabled())
					_log.debug("Seat: " + event.getOriginatingSeat() + " sent private message: '" + event.getMessage() +"' to " + event.getRecipientName());
				if(_server.getIDByName(event.getRecipientName())<0)
					error="User not valid";
				else if(!_server.isUserOnline(event.getRecipientName()))
					error="User not online";
				else if(!_server.canReceiveOLMs(event.getRecipientName())) {
					error="User cannot receive OLMs";
				} else {
					String[] msg=new String[2];
					msg[0]="Message From:  " + _server.getHandle();
					msg[1]=event.getMessage();
					_server.sendOLM(event.getRecipientName(),msg);
				} 
				if(error!=null) {
					try {
						_server.send(new AnonChatSend("*ERROR*",error));
					} catch (IOException e) {
						// leave chat, and shut down.
						_log.error("Link error",e);
						_server.terminate();
					}
				}
			}
		}

		public void userJoined(JoinEvent event) {
			if(_bSuspend)
				_alQueue.add(event);
			else {
				if(_log.isDebugEnabled())
					_log.debug(event.getName() + " enters room in seat " + event.getSeat());
				try {
					_server.send(new CA(event.getSeat(),event.getName()));
				} catch (IOException e) {
					// leave chat, and shut down.
					_log.error("Link error",e);
					_server.terminate();
				}
			}
			
		}

		public void userLeft(JoinEvent event) {
			if(_bSuspend)
				_alQueue.add(event);
			else {
				if(_log.isDebugEnabled())
					_log.debug(event.getName() + " leaves room and vacates seat " + event.getSeat());
				try {
					_server.send(new CB(event.getSeat(),event.getName()));
				} catch (IOException e) {
					// leave chat, and shut down.
					_log.error("Link error",e);
					_server.terminate();
				}
			}
		}

		public void systemSent(SystemMessageEvent event) {
			// send ourselves an OLM, as we requested it.
			String[] msg=event.getMessage();
			_server.sendOLM(_server.getHandle(),msg);
			
		}

		public void acceptingQuestions(QuestionStateEvent event) {
			try {
				_server.send(new AcceptingQuestions());
				_server.send(new AnonChatSend("","Now accepting Questions"));
			} catch (IOException e) {
				// leave chat, and shut down.
				_log.error("Link error",e);
				_server.terminate();
			}
		}

		public void rejectingQuestions(QuestionStateEvent event) {
			try {
				_server.send(new RejectingQuestions());
				_server.send(new AnonChatSend("","Question session closed"));
			} catch (IOException e) {
				// leave chat, and shut down.
				_log.error("Link error",e);
				_server.terminate();
			}
		}
	}

	public Chat(QServer server) {
		super(server);
		server.enableOLMs(true);
}
	
	public void activate() throws IOException {
		SeatInfo[] seats;
		
		// find a Lobby to enter, get our seat number, etc.
		// need to make sure no enter/leaves slip by while in here.
		_room=RoomManager.join(_server.getHandle());
		// create new listener.
		_listener=new QueuedChatEventListener();
		synchronized(_room) {
			_log.debug("Adding room listener");
			_room.addEventListener(_listener);
			_log.debug("Getting seat information");
			seats=_room.getSeatInfoList();
		}
		showRoom(seats,false);
		super.activate();
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.state.QState#execute(org.jbrain.qlink.cmd.Command)
	 */
	public boolean execute(Action a) throws IOException {
		boolean rc=false;
		QState state;
		
		if(a instanceof ChatSay) {
			_log.debug(_server.getHandle() + " says'" + ((ChatSay)a).getText() + "'");
			_room.say(((ChatSay)a).getText());
			rc=true;
		} else if(a instanceof RequestGame) {
			int id=((RequestGame)a).getID();
			String name=((RequestGame)a).getTitle();
			String type=((RequestGame)a).getType();
			boolean order=((RequestGame)a).doesSystemPickOrder();
			_log.debug(_server.getHandle() + " wants to play " + name);
			state=new PlayGame(_server,_room,id,name,type,order);
			state.activate();
		} else if(a instanceof AcceptInvite) {
			Game game=_room.getPendingGame();
			state=new PlayGame(_server,game);
			state.activate();
		} else if(a instanceof DeclineInvite) {
			Game game=_room.getPendingGame();
			game.declineInvite();
		} else if(a instanceof ListRooms) {
			_log.debug("Listing Public rooms");
			rc=true;
			_rooms=RoomManager.getRoomList();
			_roomPos=0;
			sendRoomList();
		} else if(a instanceof ListMoreRooms) {
			sendRoomList();
		} else if(a instanceof ListGames) {
			_log.debug("Listing Games");
			rc=true;
			GameInfo[] games=_room.getGameInfoList();
			int i;
			if(games.length==0) {
				_server.send(new NoGames());
			} else {
				for(i=0;i<games.length-1;i++) {
					_server.send(new GameLine(games[i].getName(),games[i].getPlayOrder(),false));
				}
				_server.send(new GameLine(games[i].getName(),games[i].getPlayOrder(),true));
			}
		} else if(a instanceof EnterPublicRoom) {
			_log.debug("Entering public room");
			enterRoom(((EnterPublicRoom)a).getRoom(),true);
			rc=true;
		} else if(a instanceof EnterPrivateRoom) {
			_log.debug("Entering private room");
			rc=true;
			enterRoom(((EnterPrivateRoom)a).getRoom(),false);
		} else if(a instanceof IdentifyUser) {
			String name=((IdentifyUser)a).getData();
			_log.debug("Trying to identify user: '" + name + "'");
			if(_server.getIDByName(name)==-1) {
				_log.debug("ID Error: No such user");
				_server.send(new UserInvalid());
			} else {
				_server.send(new BulletinLine("Location:",false));
				_server.send(new BulletinLine("Somewhere, SomeState",true));
			}
		} else if(a instanceof LocateUser) {
			String name=((LocateUser)a).getData();
			_log.debug("Trying to locate user: '" + name + "'");
			if(_server.getIDByName(name)==-1) {
				_log.debug("LOCATE Error: No such user");
				_server.send(new UserInvalid());
			} else if(!_server.isUserOnline(name)) {
				_log.debug("LOCATE Error: User is not online");
				_server.send(new UserNotOnline());
			} else {
				RoomInfo room=RoomManager.getUserLocation(name);
				if(room==null) {
					_log.debug("LOCATE Error: User is unavailable");
					_server.send(new UserUnavailable());
				} else if(room.isPublicRoom()) {
					_log.debug("LOCATE Success:  User '" + name + "' is in public room '" + room.getName() + "'");
					_server.send(new LocateUserText(room.getName()));
				} else {
					_log.debug("LOCATE Success:  User '" + name + "' is in private room '" + room.getName() + "'");
					_server.send(new UserInPrivateRoom());
				}
			}
			
		} else if(a instanceof LeaveChat) {
			// we need to ack this.
			_log.debug("Leaving chat");
			leaveRoom();
			state=new DepartmentMenu(_server);
			state.activate();
		} else if(a instanceof IgnoreUser) {
			_log.debug("Ignoring user: " + ((IgnoreUser)a).getData());
			//_server.send(new IgnoreUser());
		} else if(a instanceof DeIgnoreUser) {
			_log.debug("Not Ignoring user: " + ((DeIgnoreUser)a).getData());
			//_server.send(new DeIgnoreUser());
		} else if(a instanceof EnterAuditorium) {
			enterAuditorium();
		} else if(a instanceof EnterBoxOffice) {
			// this is not correct...
			enterAuditorium();
		} else if(a instanceof RequestToObserve) {
			String handle=((RequestToObserve)a).getHandle();
			_log.debug("User requesting to observe " + handle + "'s game.");
			//
			//_server.sendToUser(new StartGame("GAME",seats));
			//_server.send(new ObserveGame());
		} else if(a instanceof SpeakerInfo) {
			sendSpeakerInfo();
		} else if(a instanceof MR) {
			// we get this after coming back from game
			SeatInfo[] seats;
			synchronized(_room) {
				_listener.suspend();
				seats=_room.getSeatInfoList();
			}
			showRoom(seats,false);
		} else if(a instanceof QuestionNextLine) {
			_alQuestion.add(((QuestionNextLine)a).getData());
		} else if(a instanceof QuestionLastLine) {
			_alQuestion.add(((QuestionLastLine)a).getData());
			_room.say((String[])_alQuestion.toArray(new String[_alQuestion.size()]));
			_alQuestion.clear();
		}
		if(!rc)
			rc=super.execute(a);
		return rc;
	}

	public void terminate() {
		// need to leave current room, and that's it.
		leaveRoom();
	}

	/**
	 * 
	 */
	private void sendSpeakerInfo() throws IOException {
		// TODO Need to modify this to send speaker information
		sendAuditoriumText();
	}

	/**
	 * 
	 */
	private void sendRoomList() throws IOException {
		for(int i=0;i<8&& _roomPos<_rooms.length;i++) {
			_server.send(new RoomLine(_rooms[_roomPos].getName(),_rooms[_roomPos].getPopulation(),_roomPos+1==_rooms.length));
			_roomPos++;
		}
		if(_roomPos<_rooms.length) {
			_server.send(new PauseRoomInfo("Press RETURN to continue, F5 to cancel"));
		}
	}

	/**
	 * @param room
	 * @param b
	 */
	
	private void enterRoom(String name, boolean b) throws IOException {
		Room room;
		SeatInfo user;
		SeatInfo[] seats;
		int mySeat=0;
		
		_log.debug("Joining room: " + name);
		room=RoomManager.joinRoom(name,_server.getHandle(),b);
		if(room==null) {
			_log.debug("Room is full");
			// send room is full.
			_server.send(new C2());
		} else {
			leaveRoom();
			// enter new room
			_room=room;
			_listener=new QueuedChatEventListener();
			synchronized(_room) {
				_log.debug("Adding room listener");
				_room.addEventListener(_listener);
				_log.debug("Getting seat information");
				seats=_room.getSeatInfoList();
			}
			showRoom(seats,true);
		}
	}
	
	private void enterAuditorium() throws IOException {
		SeatInfo user;
		SeatInfo[] seats;
		
		leaveRoom();
		_log.debug("Joining Auditorium");
		_room=RoomManager.getAuditorium(_server.getHandle());
		_listener=new QueuedChatEventListener();
		synchronized(_room) {
			_log.debug("Adding Auditorium listener");
			_room.addEventListener(_listener);
			_log.debug("Getting seat information");
			seats=_room.getSeatInfoList();
		}
		showRoom(seats,true);
		// put us at end.
		_server.send(new CE(22,_server.getHandle()));
		_listener.resume();
		sendAuditoriumText();
		
	}

	/**
	 * 
	 */
	private void leaveRoom() {
		// remove the listener 
		_log.debug("Removing room listener");
		_room.removeEventListener(_listener);
		// suspend so no more messages will go to the client.
		_listener.suspend();
		_log.debug("Leaving current room");
		// leave this room.
		_room.leave();
	}

	/**
	 * 
	 */
	private void showRoom(SeatInfo[] seats, boolean bRoomChange) throws IOException {
		SeatInfo user;
		int mySeat=0;
	
		if(_room.isPublicRoom())
			_server.send(new EnterPublicRoom(_room.getName()));
		else
			_server.send(new EnterPrivateRoom(_room.getName()));
		_log.debug("Sending seat information");
		for(int i=0,size=seats.length;i<size;i++) {
			user=seats[i];
			if(!user.getHandle().equals(_server.getHandle()))
				if(bRoomChange)
					_server.send(new CL(user.getSeat(),user.getHandle()));
				else
					_server.send(new CA(user.getSeat(),user.getHandle()));
			else
				mySeat=user.getSeat();
		}
		_server.send(new CE(mySeat,_server.getHandle()));
		_listener.resume();
		if(!bRoomChange && checkEmail())
			_server.send(new NewMail());
	}

	/**
	 * 
	 */
	private void sendAuditoriumText() throws IOException {
	    Connection conn=null;
	    Statement stmt = null;
	    ResultSet rs = null;
	    
	    try {
	    	conn=_server.getDBConnection();
	        stmt = conn.createStatement();
	        _log.debug("Reading auditorium text");
	    	rs=stmt.executeQuery("SELECT text from auditorium_text WHERE start_date<now() AND end_date>now()");
	    	if(rs.next()) {
	    		_log.debug("Defining AuditoriumState text");
	    		TextFormatter tf=new TextFormatter();
	    		do {
	        		tf.add(rs.getString("text"));
	    		} while (rs.next());
	        	_log.info("Sending AuditoriumState Text");
	        	List l=tf.getList();
	        	int size=Math.min(l.size(),11);
	        	for(int i=0;i<size;i++) {
	        		_server.send(new AuditoriumText((String)l.get(i),i+1==size));
	        	}
	    	}
	    } catch (SQLException e) {
	    	_log.error("SQL Exception",e);
	    } finally {
	    	closeRS(rs);
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

}
