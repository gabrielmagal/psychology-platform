package br.com.mindhaven.application.usecase.interfaces;

import java.io.InputStream;

public interface MinioUseCase {
    String upload(String fileName, InputStream inputStream, long size, String contentType) throws Exception;
    String uploadBase64(String fileName, String base64, String contentType) throws Exception;
    byte[] getObjectBytes(String fileName);
    void deleteObject(String fileName);
}
