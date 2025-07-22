package cn.nirvana.vMonitor.exceptions;

/**
 * 自定义文件异常类，用于封装在文件操作或加载过程中发生的异常。
 * 这样可以将底层具体的IOException、YAMLException等统一为一种更高级别的业务异常。
 */
public class FileException extends Exception { // 继承自 Exception, 使其成为受检异常
    public FileException(String message) {
        super(message);
    }

    public FileException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileException(Throwable cause) {
        super(cause);
    }
}