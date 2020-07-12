/*
 * [The "BSD licence"]
 * Copyright (c) 2010 Ben Gruver (JesusFreke)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cn.banny.inspector.dex;

import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.apache.commons.io.IOUtils;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.DexDataStore;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.smali.smaliFlexLexer;
import org.jf.smali.smaliParser;
import org.jf.smali.smaliTreeWalker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Main class for smali. It recognizes enough options to be able to dispatch
 * to the right "actual" main.
 */
public class Smali {

    public static void assembleSmaliFile(int apiLevel, File dir, File outDexFile) throws Exception {
        LinkedHashSet<File> filesToProcess = new LinkedHashSet<>();

        if (!dir.exists()) {
            throw new IOException("Cannot find file or directory \"" + dir + "\"");
        }
        if(!dir.isDirectory()) {
        	throw new IOException("dir not exists: " + dir);
        }

        getSmaliFilesInDir(dir, filesToProcess);

        final DexBuilder dexBuilder = new DexBuilder(Opcodes.forApi(apiLevel));

        int total = filesToProcess.size();
        int dirLen = dir.getAbsolutePath().length();
        System.out.println("Prepare assembleSmaliFile total: " + total);
        Thread thread = Thread.currentThread();
        int jobs = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(jobs, 4));
        List<Future<Void>> tasks = new ArrayList<>(total);
        WorkerListener workerListener = new $WorkerListener(total);
        for (final File file: filesToProcess) {
        	AssembleSmaliWorker worker = new AssembleSmaliWorker(thread, workerListener, file, dirLen, dexBuilder, apiLevel);
            tasks.add(executor.submit(worker));
        }

        try {
            for (Future<Void> task : tasks) {
                task.get();
            }
        } finally {
            executor.shutdownNow();
        }

        DexDataStore dataStore = new FileDataStore(outDexFile);
        dexBuilder.writeTo(dataStore);
    }

    private static class AssembleSmaliWorker implements Callable<Void> {
        private final Thread thread;
        private final WorkerListener workerListener;
        private final File file;
        private final int dirLen;
        private final DexBuilder dexBuilder;
        private final int apiLevel;
        AssembleSmaliWorker(Thread thread, WorkerListener workerListener, File file, int dirLen, DexBuilder dexBuilder, int apiLevel) {
            this.thread = thread;
            this.workerListener = workerListener;
            this.file = file;
            this.dirLen = dirLen;
            this.dexBuilder = dexBuilder;
            this.apiLevel = apiLevel;
        }
        @Override
        public Void call() throws Exception {
            if(thread.isInterrupted()) {
                throw new InterruptedException("assembleSmaliFile");
            }

            workerListener.notifyBegin("assembleSmaliFile: " + file.getAbsolutePath().substring(dirLen));

            boolean errors = !assembleSmaliFile(file, dexBuilder, apiLevel);
            if(errors) {
                RuntimeException e = new RuntimeException("assembleSmaliFile failed: " + file.getAbsolutePath().substring(dirLen));
                workerListener.notifyException(e);
            }
            return null;
        }
    }

    private static void getSmaliFilesInDir(File dir, Set<File> smaliFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for(File file: files) {
                if (file.isDirectory()) {
                    getSmaliFilesInDir(file, smaliFiles);
                } else if (file.getName().endsWith(".smali")) {
                    smaliFiles.add(file);
                }
            }
        }
    }

    private static boolean assembleSmaliFile(File smaliFile, DexBuilder dexBuilder,
                                             int apiLevel)
            throws Exception {
        FileInputStream fis = null;
        InputStreamReader reader = null;
        try {
            fis = new FileInputStream(smaliFile.getAbsolutePath());
            reader = new InputStreamReader(fis, StandardCharsets.UTF_8);
            return assembleSmaliFile(reader, smaliFile, dexBuilder, apiLevel);
        } finally {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(fis);
        }
    }

    private static boolean assembleSmaliFile(Reader reader, File smaliFile, DexBuilder dexBuilder,
                                             int apiLevel)
            throws Exception {
        CommonTokenStream tokens;

        smaliFlexLexer lexer;

        lexer = new smaliFlexLexer(reader);
        lexer.setSourceFile(smaliFile);
        tokens = new CommonTokenStream(lexer);

        smaliParser parser = new smaliParser(tokens);
        parser.setVerboseErrors(false);
        parser.setAllowOdex(false);
        parser.setApiLevel(apiLevel);

        smaliParser.smali_file_return result = parser.smali_file();

        if (parser.getNumberOfSyntaxErrors() > 0 || lexer.getNumberOfSyntaxErrors() > 0) {
            return false;
        }

        CommonTree t = result.getTree();

        CommonTreeNodeStream treeStream = new CommonTreeNodeStream(t);
        treeStream.setTokenStream(tokens);

        smaliTreeWalker dexGen = new smaliTreeWalker(treeStream);
        dexGen.setApiLevel(apiLevel);

        dexGen.setVerboseErrors(false);
        dexGen.setDexBuilder(dexBuilder);
        dexGen.smali_file();

        return dexGen.getNumberOfSyntaxErrors() == 0;
    }
}