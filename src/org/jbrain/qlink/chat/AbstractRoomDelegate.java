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
	Created on Jul 26, 2005
	
 */
package org.jbrain.qlink.chat;

import java.util.*;

import org.apache.log4j.Logger;
import org.jbrain.qlink.user.QHandle;
import org.jbrain.qlink.util.QuotedStringTokenizer;

public abstract class AbstractRoomDelegate implements QRoom {
	private static Logger _log=Logger.getLogger(AbstractRoomDelegate.class);
	protected static final String SYS_NAME="System";
	private String _sName;
	//private SeatInfo[] _users = new SeatInfo[ROOM_CAPACITY];
	protected Hashtable _htUsers = new Hashtable();
	protected ArrayList _listeners = new ArrayList();
	private boolean _bPublic;
	private boolean _bLocked;
	private static Random _die=new Random();
	private static String[] _sResponses=new String[20];
	//private GameDelegate[] _userGame = new GameDelegate[ROOM_CAPACITY];
	protected ArrayList _alGames = new ArrayList();
	
	static {
		// probably should go into a DB or something
		_sResponses[0]="Signs point to yes.";
		_sResponses[1]="Yes.";
		_sResponses[2]="Reply hazy, try again.";
		_sResponses[3]="Without a doubt.";
		_sResponses[4]="My sources say no.";
		_sResponses[5]="As I see it, yes.";
		_sResponses[6]="You may rely on it.";
		_sResponses[7]="Concentrate and ask again.";
		_sResponses[8]="Outlook not so good.";
		_sResponses[9]="It is decidedly so.";
		_sResponses[10]="Better not tell you now.";
		_sResponses[11]="Very doubtful.";
		_sResponses[12]="Yes - definitely.";
		_sResponses[13]="It is certain.";
		_sResponses[14]="Cannot predict now.";
		_sResponses[15]="Most likely.";
		_sResponses[16]="Ask again later.";
		_sResponses[17]="My reply is no.";
		_sResponses[18]="Outlook good.";
		_sResponses[19]="Don't count on it.";
	}
	
	public AbstractRoomDelegate(String name, boolean bPublic, boolean bLocked) {
		if(_log.isDebugEnabled())
			_log.debug("Creating " + (bLocked?"locked":"unlocked") + " " + (bPublic?"public":"private") + " room: " + name);
		_sName=name;
		_bPublic=bPublic;
		_bLocked=bLocked;
	}

	/**
	 * @return
	 */
	public String getName() {
		return _sName;
	}

	/**
	 * @return
	 */
	public int getPopulation() {
		if(_log.isDebugEnabled())
			_log.debug("Population of '" + _sName + "' is " + _htUsers.size());
		return _htUsers.size();
	}

	/**
	 * @return
	 */
	public boolean isLocked() {
		return _bLocked;
	}

	/**
	 * @return
	 */
	public boolean isPublicRoom() {
		return _bPublic;
	}

	/**
	 * @return
	 */
	public boolean isFull() {
		return getCapacity()!=0 && getPopulation()==getCapacity();
	}

	/**
	 * @return
	 */
	public abstract int getCapacity();

	public synchronized void removeEventListener(RoomEventListener listener) {
		if(_listeners.contains(listener)) {
			_listeners.remove(listener);
		}
	}

	public synchronized void addEventListener(RoomEventListener listener) {
		_listeners.add(listener);
	}

	public String getInfo() {
		return "";
	}

	public void say(QSeat info, String text) {
		if(info!=null && !info.isIgnored() && isInRoom(info)) {
			if(text.startsWith("//") || text.startsWith("=q")) {
				processCommand((SeatInfo)info,text.substring(2));
			} else {
				send(new ChatEvent(this,info.getSeatID(),info.getHandle().toString(),text));
			}
		}
	}

	/**
	 * @return
	 */
	public GameInfo[] getGameInfoList() {
		int size;
		GameDelegate game;
		GameInfo[] info;
		synchronized(_alGames) {
			size=_alGames.size();
			info=new GameInfo[size];
			for(int i=0;i<size;i++) {
				game=(GameDelegate)_alGames.get(i);
				info[i]=new GameInfo(game.getName(),game.getPlayOrder());
			}
		}
		return info;
	}

	public QSeat getSeatInfo(QHandle handle) {
		// synchronized on the table
		return (QSeat)_htUsers.get(handle.getKey());
	}

	public void removeUser(QSeat user) {
		synchronized(_htUsers) {
			// was this seat filled?
			if(isInRoom(user)) {
				leaveSeat(user);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.QRoom#changeUserName(org.jbrain.qlink.chat.QSeat, org.jbrain.qlink.user.QHandle)
	 */
	public QSeat changeUserName(QSeat user, QHandle handle, ChatProfile profile) {
		if(isInRoom(user)) {
			SeatInfo info=(SeatInfo)user;
			if(!info.isInGame()) {
				leaveSeat(user);
				SeatInfo newUser=new SeatInfo(handle,user.getSeatID(),profile);
				newUser.setIgnore(user.isIgnored());
				takeSeat(newUser);
				return newUser;
			}
		} return null;
		
	}
	/**
	 * @param event
	 */
	protected abstract void send(RoomEvent event);

	/**
	 * @param sys_name2
	 * @param seat
	 * @param alMsg
	 */
	protected void sendSystemMessage(QSeat seat, List l) {
		send(new SystemMessageEvent(this,seat.getSeatID(),seat.getHandle().toString(),(String[])l.toArray(new String[0])));
	}

	protected int getNumber(String string) {
		int pos=-1;
		try {
			pos=Integer.parseInt(string);
		} catch (NumberFormatException e) {
			// bad cmd parm, ignore.
		}
		return pos;
	}

	protected synchronized void processEvent(RoomEvent event) {
		if(event instanceof JoinEvent) 
			processJoinEvent((JoinEvent)event);
		else if(event instanceof ChatEvent) 
			processChatEvent((ChatEvent)event);
		else if(event instanceof SystemMessageEvent) 
			processSystemMessageEvent((SystemMessageEvent)event);
	}

	protected synchronized void processJoinEvent(JoinEvent event) {
		if(event != null && _listeners.size() > 0) {
			if(event.getType()==JoinEvent.EVENT_JOIN)
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).userJoined(event);
				}
			else
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).userLeft(event);
				}
		}
	}

	protected synchronized void processQuestionStateEvent(QuestionStateEvent event) {
		if(event != null && _listeners.size() > 0) {
			if(event.getType()==QuestionStateEvent.ACCEPTING_QUESTIONS)
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).acceptingQuestions(event);
				}
			else
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).rejectingQuestions(event);
				}
		}
	}

	protected synchronized void processSystemMessageEvent(SystemMessageEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).systemSent(event);
			}
		}
	}

	protected synchronized void processChatEvent(ChatEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).userSaid(event);
			}
		}
	}

	protected void processCommand(SeatInfo info, String text) {
		ArrayList alMsg=new ArrayList();
		String error=null;
		String name=null,msg=null;
		QHandle handle;
		
		if(text != null && text.length()>0) {
			QuotedStringTokenizer st=new QuotedStringTokenizer(text);
			String cmd=st.nextToken(" ").toLowerCase();
			int pos=0;
			if(cmd.startsWith("gag") && info.getProfile().isEnabled("room.user.gag")) {
				// gag user;
				if(st.hasMoreTokens())
					name=st.nextToken("\n");
				if(name!= null) {
					handle=new QHandle(name);
					SeatInfo info2=(SeatInfo)getSeatInfo(handle);
					if(info2!=null) {
						if(info2==info) {
							_log.debug("User tried to gag themselves");
							error="You cannot gag yourself";
						} else {
							_log.debug("Gagging '" + info2.getHandle() + "'");
							info2.setIgnore(true);
							error=name + " will be ignored";
						}
					} else {
						_log.debug("User tried to gag unknown user '" + handle + "'");
						error="Error: User not in room";
					}
				}
			} else if(cmd.startsWith("ungag") && info.getProfile().isEnabled("room.user.gag")) {
				// ungag user;
				if(st.hasMoreTokens())
					name=st.nextToken("\n");
				if(name!= null) {
					handle=new QHandle(name);
					SeatInfo info2=(SeatInfo)getSeatInfo(handle);
					if(info!=null) {
						_log.debug("Ungagging '" + info2.getHandle() + "'");
						info2.setIgnore(false);
						error=name + " no longer ignored";
					} else {
						_log.debug("User tried to gag unknown user '" + handle + "'");
						error="Error: User not in room";
					}
				}
			} else if(cmd.startsWith("vio") && info.getProfile().isEnabled("tos.warning")) {
				send(new ChatEvent(this,-1,SYS_NAME,"You are violating the Q-Link Terms of Service. Please STOP NOW!"));
			} else if(cmd.startsWith("me")) {
				if(st.hasMoreTokens())
					msg=st.nextToken("\n");
				if(msg!= null) {
					_log.debug("Executing '" + text + "' from '" + info.getHandle() + "'");
					send(new ChatEvent(this,-1,"","*" + info.getHandle() + " " + msg));
				}
			} else if(cmd.startsWith("8ba")) {
				send(new ChatEvent(this,-1,SYS_NAME, _sResponses[getRoll(20)-1]));
			} else if(cmd.startsWith("roll")) {
				//roll num size
				int num=2, size=6;
				if(st.hasMoreTokens()) {
					String sNum="2",sSize="6";
					sNum=st.nextToken();
					if(st.hasMoreTokens())
						sSize=st.nextToken();
					try {
						num=Integer.parseInt(sNum);
						size=Integer.parseInt(sSize);
					} catch (Exception e) {;}
				}
				if(num>8)
					num=8;
				else if(num<1)
					num=2;
				if(size>99)
					size=99;
				else if(size<2)
					size=6;
				StringBuffer sb=new StringBuffer();
				sb.append(info.getHandle());
				sb.append(" rolled ");
				sb.append(num);
				sb.append(" ");
				sb.append(size);
				sb.append("-sided di");
				sb.append(num==1?"e:":"ce:");
				for(int i=0;i<num;i++) {
					sb.append(" ");
					sb.append(getRoll(size));
				}
				send(new ChatEvent(this,-1,SYS_NAME, sb.toString()));
			} else {
				alMsg.add("Error: " + text + " not understood");
				sendSystemMessage(info,alMsg);
			}
		} else {
			error="Error: no command specified";
		}
		if(error!=null) {
			alMsg.add(error);
			sendSystemMessage(info,alMsg);
		}
	}

	/**
	 * @param user
	 * @return
	 */
	protected boolean isInRoom(QSeat user) {
		return _htUsers.containsValue(user);
	}

	/**
	 * @param user
	 */
	protected void takeSeat(QSeat user) {
		if(_log.isDebugEnabled())
			_log.debug("Adding '" + user.getHandle() + "' to room: " + getName());
		_htUsers.put(user.getHandle().getKey(),user);
		send(new JoinEvent(this,JoinEvent.EVENT_JOIN,user.getSeatID(),user.getHandle().toString()));
	}

	/**
	 * @param user
	 */
	protected void leaveSeat(QSeat user) {
		if(_log.isDebugEnabled())
			_log.debug("Removing '" + user.getHandle() + "' from room: " + getName());
		_htUsers.remove(user.getHandle().getKey());
		send(new JoinEvent(this,JoinEvent.EVENT_LEAVE,user.getSeatID(),user.getHandle().toString()));
	}

	/**
	 * @return
	 */
	private int getRoll(int size) {
		return _die.nextInt(size)+1;
	}
}
