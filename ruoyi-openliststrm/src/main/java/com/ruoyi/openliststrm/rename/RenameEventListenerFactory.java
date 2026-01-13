package com.ruoyi.openliststrm.rename;

/**
 * @author: Jack
 * @creat: 2026/1/13 11:13
 */
public interface RenameEventListenerFactory {

    RenameEventListener create(Integer taskId);

}
