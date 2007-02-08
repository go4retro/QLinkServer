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
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.jbrain.qlink.*;
import org.jbrain.qlink.chat.*;
import org.jbrain.qlink.cmd.action.*;

public class PlayGame extends AbstractState {
	private static Logger _log=Logger.getLogger(PlayGame.class);
	private static Timer _timer=new Timer();
	private QState _intState;
	private boolean _bSystemPickOrder;
	private String _sType;
	private String _sName;
	private Room _room;
	private Game _game;
	private boolean _bInvited;
	private int _iGameID;
	private ArrayList _alInvitees=new ArrayList();
	private class WaitTask extends TimerTask {
		public void run() {
			try {
				// too much time has elapsed...
				_log.debug("Timeout occurred");
				List l=_game.getAbstainList();
				SeatInfo info;
				for(int i=0,size=l.size();i<size;i++) {
					info=(SeatInfo)l.get(i);
					if(_log.isDebugEnabled()) 
						_log.debug(info.getHandle() + " did not respond to request." );
					_server.send(new PlayerNoResponse(info.getSeat()));
				}
				if(l.size()>0)
					restoreState();
			} catch (IOException e) {
				_log.error("Link error",e);
				_server.terminate();
			}
		}
	};

	private GameEventListener _listener=new GameEventListener() {

		public void gameSent(GameCommEvent event) {
			if(!event.getHandle().equals(_server.getHandle())) {
				if(_log.isDebugEnabled())
					_log.debug("Seat: " + event.getSeat() + " sent game data: '" + event.getText() +"'");
				try {
					_server.send(new GameSend(event.getSeat(),event.getText()));
				} catch (IOException e) {
					// leave chat, and shut down.
					_log.error("Link error",e);
					_server.terminate();
				}
			}
		}

		public void eventOccurred(GameEvent event) {
			switch(event.getType()) {
				case GameEvent.LEAVE_GAME:
					if(!event.getHandle().equals(_server.getHandle())) {
						_log.debug(event.getHandle() + " left game");
						try {
							_server.send(new PlayerLeftGame(event.getHandle()));
						} catch (IOException e) {
							// leave game, and shut down.
							_log.error("Link error",e);
							_server.terminate();
						}
					}
					break;
				case GameEvent.ACCEPT_INVITE:
					if(!_bInvited) {
						_log.debug(event.getHandle() + " accepted game invite");
						if(_game.canContinue()) {
							_waitTask.cancel();
							_game.requestLoad();
						}
					}
					break;
				case GameEvent.REQUEST_LOAD:
					_log.debug("Sending game load command");
					try {
						_server.send(new LoadGame());
					} catch (IOException e) {
						// leave game, and shut down.
						_log.error("Link error",e);
						_server.terminate();
					}
					break;
				case GameEvent.DECLINE_INVITE:
					if(!_bInvited) {
						_log.debug(event.getHandle() + " declined game invite");
						try {
							_server.send(new PlayerDeclinedInvite(event.getSeat()));
						} catch (IOException e) {
							// leave game, and shut down.
							_log.error("Link error",e);
							_server.terminate();
						}
					}
					break;
				case GameEvent.READY_TO_START:
					if(!_bInvited) {
						_log.debug(event.getHandle() + " loaded the game and requests the start");
						if(_game.canContinue()) {
							_log.debug("Sending game start commands");
							SeatInfo[] players=_game.getPlayers();
							byte[] seats=_game.getPlayOrder();
							for(int i=0;i<players.length;i++) {
								_server.sendToUser(players[i].getHandle(),new StartGame("GAME",seats));
							}
						}
					}
					break;
				case GameEvent.REQUEST_RESTART:
					if(!event.getHandle().equals(_server.getHandle())) {
						_log.debug(event.getHandle() + " requests a game restart");
						try {
							_server.send(new RequestGameRestart());
						} catch (IOException e) {
							// leave game, and shut down.
							_log.error("Link error",e);
							_server.terminate();
						}
					}
					break;
				case GameEvent.ACCEPT_RESTART:
					if(!_bInvited) {
						_log.debug(event.getHandle() + " accepts a game restart");
						if(_game.canContinue()) {
							// not sure, but we will try...
							_log.debug("Sending game restart commands");
							SeatInfo[] players=_game.getPlayers();
							byte[] seats=_game.getPlayOrder();
							for(int i=0;i<players.length;i++) {
								_server.sendToUser(players[i].getHandle(),new RestartGame());
							}
						}
					}
			}
		}
		
	};
	private WaitTask _waitTask;
	private boolean _bInGame=false;

	public PlayGame(QServer server, Game game) {
		super(server);
		_game=game;
		_bInvited=true;
	}
	
	/**
	 * 
	 */
	protected void restoreState() {
		_game.removeListener(_listener);
		_game.leave();
		_server.setState(_intState);
	}

	public PlayGame(QServer server, Room room, int id, String name, String type, boolean bSystemPickOrder) {
		super(server);
		_bSystemPickOrder=bSystemPickOrder;
		_sType=type;
		_sName=name;
		_room=room;
		_iGameID=id;
		_bInvited=false;
}
	
	public void activate() throws IOException {
		if(!_bInvited) {
			_log.debug("User request to play a game");
			_game=_room.createGame(_iGameID,_sName,_sType,_bSystemPickOrder);
			if(_game!=null) {
				_log.debug("Game service successfully created");
				_game.addListener(_listener);
				_intState=_server.getState();
				super.activate();
			} else {
				_log.debug("Game service creation failed");
			}
		} else {
			_log.debug("User invited to play a game");
			if(_game!=null) {
				_game.addListener(_listener);
				_intState=_server.getState();
				super.activate();
				_game.acceptInvite();
			} else {
				_log.debug("Game no longer active.  Must have timed out during invite");
				_server.send(new GameError("Game invitation has been cancelled"));
			}
		}
	}
	

	public boolean execute(Action a) throws IOException {
		boolean rc=false;
		SeatInfo[] players;
		byte[] seats;
		SeatInfo invitee;
		String handle;
		
		if(!_bInvited) {
			if(a instanceof GameNextPlayer) {
				_alInvitees.add(((GameNextPlayer)a).getHandle());
				rc=true;
			} else if(a instanceof GameLastPlayer) {
				rc=true;
				_alInvitees.add(((GameLastPlayer)a).getHandle());
				// are all of them free to play and present?
				for(int i=0;i<_alInvitees.size();i++) {
					handle=(String)_alInvitees.get(i);
					invitee=_room.getSeatInfo(handle);
					if(invitee==null) {
						// player has left before we could invite him/her
						_server.send(new PlayerNotInRoomError(handle));
						_game.clearPlayers();
						break;
					} else if(invitee.isInGame()) {
						_server.send(new PlayerInGameError(invitee.getSeat()));
						// they are already in a pending game
						_game.clearPlayers();
						break;
					} else {
						// add player
						_game.addPlayer(handle);
					}
				}
				players=_game.getPlayers();
				if(players.length!=0) {
					seats=_game.getPlayOrder();
					// move to next step of game.
					// accept the pseudoInvite for our client.
					_game.acceptInvite();
					for(int i=0;i<players.length;i++) {
						if(!players[i].getHandle().equalsIgnoreCase(_server.getHandle())) {
							_server.sendToUser(players[i].getHandle(),new InviteToGame(_bSystemPickOrder, players[i].getSeat(), _iGameID, _sName, seats));
						}
					}
					_server.send(new PrepGame(seats));
					_log.debug("Setting timeout for responses");
					// need to set a timer...
					_waitTask=new WaitTask(); 
					// schedule it for 30 seconds.
					_timer.schedule(_waitTask,30000);
				} else {
					_log.debug("No players to invite, switch back to previous state");
					restoreState();
				}
			}
		}
		if(!rc){
			if(a instanceof GameMove) {
				_log.debug("Game sent data");
				_game.send(((GameMove)a).getText());
				rc=true;
			} else if(a instanceof RequestGameStart) {
				_log.debug("Loaded game, ready to play");
				_game.readyToStart();
				rc=true;
			} else if(a instanceof RequestGameRestart) {
				_log.debug("Request restart game");
				_game.requestRestart();
				rc=true;
			} else if(a instanceof AcceptRestart) {
				_log.debug("Accept restart game");
				_game.acceptRestart();
				rc=true;
			} else if(a instanceof LeaveGame) {
				_log.debug("Player is leaving game");
				restoreState();
				rc=true;
			} else if(a instanceof SuspendServiceAck && _bInGame) {
				/* 
				 * This is a kludge, as when the game first loads, if you leave
				 * no LeaveGame cmd is sent.
				 */
				_log.debug("Player is leaving game (implied)");
				restoreState();
				rc=_intState.execute(a);
			} else if(a instanceof ResumeService) {
				_bInGame=true;
				rc=_intState.execute(a);
			} else { 
				rc=_intState.execute(a);
			}
		}
		return rc;
	}


	public void terminate() {
		_game.removeListener(_listener);
		_game.leave();
		_intState.terminate();
	}
}
