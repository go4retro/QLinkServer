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
	Created on Aug 31, 2005
	
 */
package org.jbrain.qlink.io;

import java.io.IOException;
import java.io.InputStream;


public class EscapedInputStream extends InputStream {
	private static final int ESCAPE=0x5D;
	private static final int ESCAPE_00=0x55;
	private static final int ESCAPE_0D=0x58;
	private static final int ESCAPE_0E=0x5B;
	private static final int ESCAPE_5D=0x8;
	private static final int ESCAPE_FF=0xAA;
	private InputStream _is;
	private int _iEscape;
	
	public EscapedInputStream(InputStream is) {
		_is=is;
		_iEscape=-1;
	}
	
	public int read() throws IOException {
		int rc;
		
		if(_iEscape>-1) {
			rc=_iEscape;
			_iEscape=-1;
		} else {
			rc=_is.read();
			switch(rc) {
				case 0x00:
					_iEscape=ESCAPE_00;
					rc=ESCAPE;
					break;
				case 0x0d:
					_iEscape=ESCAPE_0D;
					rc=ESCAPE;
					break;
				case 0x0e:
					_iEscape=ESCAPE_0E;
					rc=ESCAPE;
					break;
				case 0x5d:
					_iEscape=ESCAPE_5D;
					rc=ESCAPE;
					break;
				case 0xff:
					_iEscape=ESCAPE_FF;
					rc=ESCAPE;
					break;
			}
		}
		return rc;
	}

}
