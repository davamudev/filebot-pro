#!/usr/bin/env filebot-pro -script
// Modified for FileBot Pro on 2026-10-07, licensed under the GPL-3.0, see README.md


osdbUser = any{ osdbUser }{ console.printf('Enter OpenSubtitles username: '); console.readLine() }
// the password is not echoed to the terminal (avoid --def osdbPwd: command line arguments are visible to other processes)
osdbPwd = any{ osdbPwd }{ def pwd = System.console().readPassword('Enter OpenSubtitles password: '); pwd ? new String(pwd) : null }


/* --------------------------------------------------------------------- */


// set login details
if (osdbUser && osdbPwd) {
	log.config('Set OpenSubtitles login details')
	try {
		// check login details before storing them
		WebServices.OpenSubtitles.setUser(osdbUser, osdbPwd)
		printAccountInformation()
		WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, osdbUser, osdbPwd)
	} catch(e) {
		if (e.message =~ /Login failed|401/) {
			log.warning 'Your login details are incorrect. Please go to www.opensubtitles.com to check your login details and reset your password if necessary.'
		}
		die e.message
	}
}


// clear login details
if (!osdbUser && !osdbPwd) {
	log.config('Reset OpenSubtitles login details')
	WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, null, null)
}




/**
 * Log in and retrieve account details.
 */
void printAccountInformation() {
	console.printf('Checking... ')
	def info = WebServices.OpenSubtitles.getUserInfo()
	console.printf('OK\n\n')

	info.each{ n, v ->
		log.config("$n: $v")
	}
}
