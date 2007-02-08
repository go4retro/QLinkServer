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
import org.jbrain.qlink.QServer;
import org.jbrain.qlink.util.QuotedStringTokenizer;

class RoomDelegate {
	private static Logger _log=Logger.getLogger(RoomDelegate.class);
	public static final int ROOM_CAPACITY=23;
	private String _sName;
	private SeatInfo[] _users=new SeatInfo[ROOM_CAPACITY];
	private Hashtable _htUsers=new Hashtable();
	private ArrayList _listeners=new ArrayList();
	private boolean _bPublic;
	private boolean _bLocked;
	private static Random _die=new Random();
	private static String[] _sResponses=new String[20];
	private GameDelegate[] _userGame=new GameDelegate[ROOM_CAPACITY];
	private ArrayList _alGames=new ArrayList();
	
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
	
	public RoomDelegate(String name, boolean bPublic, boolean bLocked) {
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
		_log.debug("Population of '" + _sName + "' is " + _htUsers.size());
		return _htUsers.size();
	}

	/**
	 * @param handle
	 */
	public int addUser(String handle) {
		SeatInfo user=(SeatInfo)_htUsers.get(handle);
		
		if(user==null) {
			if(_log.isDebugEnabled())
				_log.debug("Adding '" + handle + "' to room: " + _sName);
			synchronized(_htUsers) {
				if(!isFull()) {
					for(int i=0;i<ROOM_CAPACITY;i++){
						if(_users[i]==null) {
							user=new SeatInfo(handle,i);
							_users[i]=user;
							_htUsers.put(handle,user);
							processEvent(new JoinEvent(this,JoinEvent.EVENT_JOIN,i,user.getHandle()));
							return i;
						}
					}
				}
			}
			return -1;
		} else {
			if(_log.isDebugEnabled())
				_log.debug("'" + handle + "' is already in room: " + _sName);
			return user.getSeat();
		}
	}

	/**
	 * @return
	 */
	public boolean isFull() {
		return getPopulation()==ROOM_CAPACITY;
	}

	public SeatInfo[] getSeatInfoList() {
		return (SeatInfo[])_htUsers.values().toArray(new SeatInfo[0]);
	
	}
	
	public void removeUser(int seat) {
		synchronized(_htUsers) {
			// was this seat filled?
			if(_users[seat]!=null) {
				if(_log.isDebugEnabled())
					_log.debug("Removing '" + _users[seat].getHandle() + "' from room: " + _sName);
				_htUsers.remove(_users[seat].getHandle());
				// remove any pending game.
				_userGame[seat]=null;
				processEvent(new JoinEvent(this,JoinEvent.EVENT_LEAVE,seat,_users[seat].getHandle()));
				_users[seat]=null;
			}
		}
	}
	
	public void say(int seat, String text) {
		if(text.startsWith("//")) {
			String name=null,msg=null;
			QuotedStringTokenizer st=new QuotedStringTokenizer(text);
			String cmd=st.nextToken(" ").toLowerCase();
			int pos=0;
			if(cmd.startsWith("//msg")) {
				// Send someone a private msg;
				if(st.hasMoreTokens())
					name=st.nextToken(" ");
				if(st.hasMoreTokens())
					msg=st.nextToken("\n");
				if(name != null && msg!= null) {
					_log.debug("Executing //msg from seat " + seat + " to " + name);
					processEvent(new MessageEvent(this,seat,name,msg));
				}
			} else if(cmd.startsWith("//sysmsg") && !this.isPublicRoom()) {
				// Send SYSOLM;
				if(st.hasMoreTokens())
					msg=st.nextToken("\n");
				if(msg!= null) {
					_log.debug("Executing //sysmsg from seat " + seat);
					QServer.sendSYSOLM(msg);
				}
			} else if(cmd.startsWith("//me")) {
				if(st.hasMoreTokens())
					msg=st.nextToken("\n");
				if(msg!= null) {
					_log.debug("Executing //me " + text + " from seat " + seat);
					processEvent(new ChatEvent(this,"","*" + _users[seat].getHandle() + " " + msg));
				}
			} else if(cmd.startsWith("//8ba")) {
				processEvent(new ChatEvent(this,"System", _sResponses[getRoll(20)-1]));
			} else if(cmd.startsWith("//roll")) {
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
				sb.append(_users[seat].getHandle());
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
				processEvent(new ChatEvent(this,"System", sb.toString()));
			} else {
				processEvent(new ChatEvent(this,seat,text));
			}
		} else
			processEvent(new ChatEvent(this,seat,text));
	}

	/**
	 * @return
	 */
	private int getRoll(int size) {
		return _die.nextInt(size)+1;
	}

	public synchronized void addEventListener(RoomEventListener listener) {
		_listeners.add(listener);
	}

	public synchronized void removeEventListener(RoomEventListener listener) {
		if(_listeners.contains(listener)) {
			_listeners.remove(listener);
		}
	}

	public synchronized void processJoinEvent(JoinEvent event) {
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

	public synchronized void processQuestionStateEvent(QuestionStateEvent event) {
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

	public synchronized void processMessageEvent(MessageEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).userSaid(event);
			}
		}
	}

	public synchronized void processSystemMessageEvent(SystemMessageEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).systemSent(event);
			}
		}
	}

	public synchronized void processChatEvent(ChatEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).userSaid(event);
			}
		}
	}

	protected synchronized void processEvent(RoomEvent event) {
		if(event instanceof JoinEvent) 
			processJoinEvent((JoinEvent)event);
		else if(event instanceof MessageEvent) 
			processMessageEvent((MessageEvent)event);
		else if(event instanceof ChatEvent) 
			processChatEvent((ChatEvent)event);
		else if(event instanceof SystemMessageEvent) 
			processSystemMessageEvent((SystemMessageEvent)event);
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
	public boolean isLocked() {
		return _bLocked;
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

	/**
	 * @param name
	 * @param type
	 * @param systemPickOrder
	 * @return
	 */
	public GameDelegate createGame(int id, String name, String type, boolean systemPickOrder) {
		GameDelegate game=new GameDelegate(this,id,name,type,systemPickOrder);
		synchronized(_alGames) {
			_alGames.add(game);
		}
		return game;
	}
	
	public void addGameUser(int seat, GameDelegate game) {
		_userGame[seat]=game;
		_users[seat].setGameStatus(true);
	}
	
	public void removeGameUser(int seat) {
		_userGame[seat]=null;
		_users[seat].setGameStatus(false);
	}
	
	public GameDelegate getGame(int seat) {
		GameDelegate game=_userGame[seat];
		// turn off pending status;
		_users[seat].setGameStatus(false);
		return game;
	}

	/**
	 * @param seat
	 * @return
	 */
	public SeatInfo getSeatInfo(int seat) {
		return _users[seat];
	}
	
	public SeatInfo getSeatInfo(String handle) {
		return (SeatInfo)_htUsers.get(handle);
	}

	/**
	 * @param delegate
	 */
	public void destroyGame(GameDelegate game) {
		// need to reset all users
		for(int i=0;i<ROOM_CAPACITY;i++) {
			if(_userGame[i]!=null) {
				_users[i].setGameStatus(false);
				_userGame[i]=null;
			}
		}
		synchronized(_alGames) {
			_alGames.remove(game);
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
}
