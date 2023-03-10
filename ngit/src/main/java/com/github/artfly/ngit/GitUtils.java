package com.github.artfly.ngit;

import com.github.artfly.ngit.exception.GitIOException;
import com.github.artfly.ngit.model.GitBlob;
import com.github.artfly.ngit.model.GitObjHeader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class GitUtils {

    private static final int BUF_SIZE = 1024;

    public static void write(@NotNull Path outPath, @NotNull GitBlob blob) {
        try (OutputStream out = zipped(outPath)) {
            out.write(blob.header().bytes());
            try (InputStream is = new BufferedInputStream(new FileInputStream(blob.original().toFile()))) {
                is.transferTo(out);
            }
        } catch (IOException e) {
            throw new GitIOException(e);
        }
    }

    public static void unzip(@NotNull Path gitObjPath, @NotNull OutputStream where) {
        try (InputStream gitObj = unzipped(gitObjPath)) {
            skipHeader(gitObj);
            gitObj.transferTo(where);
        } catch (IOException e) {
            throw new GitIOException(e);
        }
    }

    private static void skipHeader(@NotNull InputStream inputStream) throws IOException {
        GitObjHeader.fromBytes(inputStream);
    }

    public static @NotNull Path gitPath(@NotNull GitBlob blob) {
        String sha1 = encodeHex(sha1(blob));
        return Path.of(sha1.substring(0, 2), sha1.substring(2));
    }

    private static @NotNull String encodeHex(byte @NotNull [] digest) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static byte @NotNull [] sha1(@NotNull GitBlob blob) {
        MessageDigest messageDigest = getSha1Digest();

        messageDigest.update(blob.header().bytes());

        messageDigest.update((byte) 0);

        byte[] buffer = new byte[BUF_SIZE];
        try (InputStream is = new BufferedInputStream(new FileInputStream(blob.original().toFile()))) {
            int nRead;
            while ((nRead = is.read(buffer)) > 0) {
                messageDigest.update(buffer, 0, nRead);
            }
        } catch (IOException e) {
            throw new GitIOException(e);
        }

        return messageDigest.digest();
    }

    private static @NotNull MessageDigest getSha1Digest() {
        try {
            return MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Contract("_ -> new")
    private static @NotNull OutputStream zipped(@NotNull Path outPath) throws IOException {
        Files.createDirectories(outPath.getParent());
        Files.createFile(outPath);
        return new DeflaterOutputStream(new FileOutputStream(outPath.toFile()));
    }

    private static @NotNull InputStream unzipped(@NotNull Path inPath) throws IOException {
        return new InflaterInputStream(new FileInputStream(inPath.toFile()));
    }
}
