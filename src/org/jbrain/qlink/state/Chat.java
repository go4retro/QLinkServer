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
import java.util.List;

import org.apache.log4j.Logger;
import org.jbrain.qlink.*;
import org.jbrain.qlink.chat.*;
import org.jbrain.qlink.cmd.action.*;
import org.jbrain.qlink.text.TextFormatter;
import org.jbrain.qlink.util.QuotedStringTokenizer;

public class Chat extends AbstractState {
	private static Logger _log=Logger.getLogger(Chat.class);

	/**
	 * 
	 * @uml.property name="_room"
	 * @uml.associationEnd multiplicity="(0 1)"
	 */
	private Room _room;

	private int _iSeat;

	/**
	 * 
	 * @uml.property name="_listener"
	 * @uml.associationEnd inverse="this$0:org.jbrain.qlink.state.Chat$QueuedChatEventListener"
	 * multiplicity="(0 1)"
	 */
	private QueuedChatEventListener _listener;

	/**
	 * 
	 * @uml.property name="_rooms"
	 * @uml.associationEnd multiplicity="(0 -1)"
	 */
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
				if(e instanceof ChatEvent)
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
						_log.debug(event.getOriginatingName() + " says: '" + event.getText() +"'");
					try {
						_server.send(new AnonChatSend(event.getOriginatingName(),event.getText()));
					} catch (IOException e) {
						// leave chat, and shut down.
						_log.error("Link error",e);
						_server.terminate();
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
			_server.sendOLM(true,msg);
			
		}

		public void acceptingQuestions(QuestionStateEvent event) {
			try {
				_server.send(new AcceptingQuestions());
			} catch (IOException e) {
				// leave chat, and shut down.
				_log.error("Link error",e);
				_server.terminate();
			}
		}

		public void rejectingQuestions(QuestionStateEvent event) {
			try {
				_server.send(new RejectingQuestions());
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
			_log.debug(_server.getHandle() + " says: '" + ((ChatSay)a).getText() + "'");
			process(((ChatSay)a).getText());
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
			// we bypass the process function for this.
			_room.say((String[])_alQuestion.toArray(new String[_alQuestion.size()]));
			_alQuestion.clear();
		} else if(a instanceof IncludeMe) {
			_server.send(new PartnerSearchMessage("This is not implemented yet"));
		} else if(a instanceof ExcludeMe) {
			_server.send(new PartnerSearchMessage("This is not implemented yet"));
		} else if(a instanceof PartnerSearchStatusRequest) {
			_server.send(new PartnerSearchMessage("This is not implemented yet"));
		} else if(a instanceof CancelPartnerSearch) {
			_server.send(new PartnerSearchMessage("This is not implemented yet"));
		} else if(a instanceof FindPartners) {
			_log.debug(_server.getHandle() + " wants system to pick partners for " + ((FindPartners)a).getTitle());
			_server.send(new FindPartnersAck());
		} else if(a instanceof SelectPartner) {
			_log.debug("Adding " + ((SelectPartner)a).getHandle() + " to partner list");
			
		} else if(a instanceof FindMorePartners) {
			_log.debug("System needs to find  " + ((FindMorePartners)a).getNumberToFind());
			_server.send(new FindPartnersAck());
		}
		if(!rc)
			rc=super.execute(a);
		return rc;
	}

	/**
	 * @param text
	 */
	private void process(String text) throws IOException {
		if(text.startsWith("//") || text.startsWith("=q")) {
			// do //msg and //join here.
			String[] olm;
			String name=null,msg=null, error=null;
			QuotedStringTokenizer st=new QuotedStringTokenizer(text.substring(2));
			String cmd=st.nextToken(" ").toLowerCase();
			int pos=0;
			if(cmd.startsWith("msg")) {
				// Send someone a private msg;
				if(st.hasMoreTokens())
					name=st.nextToken(" ");
				if(st.hasMoreTokens())
					msg=st.nextToken("\n");
				if(name != null && msg!= null) {
					if(_log.isDebugEnabled())
						_log.debug("sending private message: '" + msg +"' to " + name);
					if(_server.getIDByName(name)<0)
						error="User not valid";
					else if(!_server.isUserOnline(name))
						error="User not online";
					else if(!_server.canReceiveOLMs(name)) {
						error="User cannot receive OLMs";
					} else {
						olm=new String[2];
						olm[0]="Message From:  " + _server.getHandle();
						olm[1]=msg;
						_server.sendOLM(name,olm);
					}
				}
			} else if(cmd.startsWith("joi")) {
				// join a new room;
				if(st.hasMoreTokens()) {
					name=st.nextToken(" ");
					if(name != null) {
						if(_log.isDebugEnabled())
							_log.debug("joining public room: " + name);
						enterRoom(name,true);
					}
				} else {
					error ="No room specified";
				}
			} else if(cmd.startsWith("pjoi")) {
				// join a new room;
				if(st.hasMoreTokens()) {
					name=st.nextToken(" ");
					if(name != null) {
						if(_log.isDebugEnabled())
							_log.debug("joining private room: " + name);
						enterRoom(name,false);
					}
				} else {
					error ="No room specified";
				}
			} else
				_room.say(text);
			// did we have an error.
			if(error!=null) {
				olm=new String[1];
				olm[0]="Error: " + error;
				_server.sendOLM(true,olm);
			}
		} else {
			// can room make sense of it?
			_room.say(text);
		}
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
		_room=RoomManager.joinAuditorium(_server.getHandle());
		_listener=new QueuedChatEventListener();
		synchronized(_room) {
			_log.debug("Adding Auditorium listener");
			_room.addEventListener(_listener);
			_log.debug("Getting seat information");
			seats=_room.getSeatInfoList();
		}
		_server.send(new EnterPublicRoom(_room.getName()));
		_log.debug("Sending seat information");
		for(int i=0,size=seats.length;i<size;i++) {
			user=seats[i];
			_server.send(new CL(user.getSeat(),user.getHandle()));
		}
		// put us at end.
		_server.send(new CE(22,_server.getHandle()));
		_listener.resume();
		sendAuditoriumText();
		if(((Auditorium)_room).isAcceptingQuestions())
			_server.send(new AcceptingQuestions());
		else
			_server.send(new RejectingQuestions());
		
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
		_log.debug("Defining Auditorium text");
		String text=_room.getInfo();
		TextFormatter tf=new TextFormatter();
		tf.add(text);
		tf.add("\n     <PRESS F5 TO CONTINUE>");
    	_log.info("Sending Auditorium Text");
    	List l=tf.getList();
    	int size=l.size();
    	for(int i=0;i<size;i++) {
    		_server.send(new AuditoriumText((String)l.get(i),i+1==size));
    	}
	}

}
