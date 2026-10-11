/*
 * Created on 19.03.2005
 */

package dev.davamu.filebotpro.util.ui.notification;

public interface NotificationLayout {

	public void add(NotificationWindow notification);

	public void remove(NotificationWindow notification);

	public int size();

}
