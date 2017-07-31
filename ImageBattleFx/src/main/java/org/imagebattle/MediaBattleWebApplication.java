package org.imagebattle;

import static j2html.TagCreator.a;
import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.form;
import static j2html.TagCreator.head;
import static j2html.TagCreator.html;
import static j2html.TagCreator.img;
import static j2html.TagCreator.input;
import static j2html.TagCreator.link;
import static j2html.TagCreator.span;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.title;
import static j2html.TagCreator.tr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import javafx.util.Pair;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.io.EofException;
import spark.Response;
import spark.Spark;
import spark.utils.IOUtils;

public class MediaBattleWebApplication {

  // TODO use icon
  // TODO use title
  // TODO during battle show current ranking of the candidates (wins,losses, place)

  private static final String FILES = "/files/";

  private static Logger log = LogManager.getLogger();

  private final Map<String, ImageBattleFolder> folders = new HashMap<>();

  private final Map<File, MusicFile> musicFiles = new HashMap<>();

  public MediaBattleWebApplication(CentralStorage centralStorage) {

    centralStorage.folders().forEach(f -> folders.put(f.getName(), f));

    /*
     * Folder = Directory with a media type and path. The folder should have a name that is the url.
     */
    Spark.before("*", (q, a) -> log.trace("request uri: " + q.uri()));

    Spark.exception(Exception.class, (exception, request, response) -> {
      log.catching(exception);
      response.body(stackTraceString(exception));
    });

    hostBattle();

    hostFiles();

    handleDecision();

    handleReset();

    Spark.get("/style/:name",
        (request, response) -> getResource("/style/" + request.params("name")));

    Spark.get("/folders", (request, response) -> foldersView());

    Spark.get("/folders/add", (request, response) -> getResource("/folders_add.html"));

    Spark.post("/folders/add", (request, response) -> {
      String folderName = request.queryParams("name");
      String mediaTypeString = request.queryParams("media_type");
      String directory = request.queryParams("directory");
      boolean isRecursive = request.queryParams("recursive") != null;

      MediaType mediaType = MediaType.valueOf(mediaTypeString);
      File dirFile = new File(directory);
      if (dirFile.isDirectory()) {
        ImageBattleFolder f = new ImageBattleFolder(centralStorage, dirFile, mediaType, isRecursive,
            folderName);
        folders.put(folderName, f);
        centralStorage.addFolder(f);
        return foldersView();

      }

      return getResource("/folders_add.html");
    });

    // result list
    Spark.get("/results/:folder", (request, result) -> {
      String folderName = request.params("folder");
      ImageBattleFolder folder = folders.get(folderName);
      boolean image = MediaType.IMAGE.equals(folder.getMediaType());
      return image ? imageResultView(folderName, folder)
          : audioResultView(folderName, folder);
    });
  }

  private ContainerTag foldersView() {
    return html().with(
        head()
            .with(link().withHref("/style/folders.css").withRel("stylesheet").withType("text/css")),
        head().with(link().withHref("/style/table.css").withRel("stylesheet").withType("text/css")),
        body().with(
            a("add").withHref("/folders/add"),
            table().with(
                thead().with(
                    th("Name"),
                    th("Path"),
                    th("Media Type"),
                    th("Recursive"),
                    th("Battle"),
                    th("Results")),
                tbody().with(
                    each(folders.entrySet(),
                        entry -> tr().with(
                            td(entry.getKey()),
                            td(entry.getValue().getDirectory().getAbsolutePath()),
                            td(entry.getValue().getMediaType().name()),
                            td(String.valueOf(entry.getValue().isRecursive())),
                            td().with(a("Battle").withHref("/battle/" + entry.getKey())),
                            td().with(a("Results").withHref("/results/" + entry.getKey()))))))));
  }

  private void handleDecision() {
    Spark.post("/choose/:folder", (request, result) -> {
      String folderName = request.params("folder");
      ImageBattleFolder folder = folders.get(folderName);

      String ignoreButton = request.queryParams("ignoreButton");
      log.debug("ignoreButton: " + ignoreButton);
      if ("Ignore".equals(ignoreButton)) {
        folder.ignoreFile(new File(folder.getDirectory(), request.queryParams("ignore")));
      }

      String choose = request.queryParams("choose");
      log.debug("choose : " + choose);
      if ("Choose".equals(choose)) {

        String winner = request.queryParams("winner");
        String loser = request.queryParams("loser");

        log.debug("winner: " + winner);
        log.debug("loser : " + loser);

        File winnerFile = new File(folder.getDirectory(), winner);
        File loserFile = new File(folder.getDirectory(), loser);

        folder.makeDecision(winnerFile, loserFile);

      }
      result.redirect("/battle/" + folderName);

      return result;
    });
  }

  private void handleReset() {
    Spark.post("/reset/:folder/:file", (request, result) -> {
      String folderName = request.params("folder");
      String fileName = request.params("file");
      ImageBattleFolder folder = folders.get(folderName);
      folder.reset(new File(folder.getDirectory(), fileName));

      result.redirect("/results/" + folderName);

      return result;
    });
  }

  private void hostFiles() {
    Spark.get(FILES + ":folder/*",
        (request, result) -> Optional.of(request.params("folder"))
            .map(folders::get)
            .map(ImageBattleFolder::getDirectory)
            .map(dir -> musicFile(dir.getAbsolutePath(), request.splat()[0], result))
            .orElseThrow(() -> new IllegalArgumentException()));
  }

  private void hostBattle() {
    Spark.get("/battle/:folder", (request, result) -> {
      String folderName = request.params("folder");
      ImageBattleFolder folder = Optional.ofNullable(folderName)
          .map(folders::get)
          .orElseThrow(
              () -> new IllegalArgumentException("folder not found: " + request.params("folder")));
      File directory = folder.getDirectory();

      List<String> list = Arrays.asList("Winner Oriented", "SameWinLoseRatio");

      String chooser = list.get(new Random().nextInt(list.size()));
      log.debug("chooser: {}  ", chooser);

      boolean image = MediaType.IMAGE.equals(folder.getMediaType());

      folder.setChoosingAlgorithm(chooser);
      return folder.getNextToCompare()
          .map(pair -> image ? imageBattle(pair, folderName, directory)
              : audioBattle(pair, folderName, directory))
          .orElseGet(() -> audioResultView(folderName, folder));
    });
  }

  public static void main(String[] args) {
    CentralStorage storage = new CentralStorage(CentralStorage.SQLITE_FILE);

    new MediaBattleWebApplication(storage);

  }

  private String audioResultView(String folderName, ImageBattleFolder folder) {
    log.debug("start");
    // System.err.println(folder.jsonGraph());

    String directory = "D:\\tech\\graphviz\\";
    String fileName = folderName;

    folder.writeGraphImage(directory, fileName);

    String result = html().with(
        head().with(
            title("Results - " + folderName),
            link().withHref("/style/audio_results.css").withRel("stylesheet").withType("text/css")),
        head().with(link().withHref("/style/table.css").withRel("stylesheet").withType("text/css")),
        body().with(
            a("Battle").withHref("/battle/" + folderName),
            table().with(
                thead().with(
                    th("Place"),
                    th("Wins"),
                    th("Losses"),
                    th("Title"),
                    th("Album"),
                    th("Artist"),
                    th("File Name")),
                tbody().with(
                    each(folder.getResultList(), entry -> {
                      MusicFile musicFile = musicFiles.computeIfAbsent(entry.file,
                          MusicFile::create);
                      return tr().with(
                          td(String.valueOf(entry.ignored ? "ignored" : entry.place)),
                          td(String.valueOf(entry.wins)),
                          td(String.valueOf(entry.loses)),
                          td(String.valueOf(musicFile.getTitle())),
                          td(String.valueOf(musicFile.getAlbum())),
                          td(String.valueOf(musicFile.getArtist())),
                          td(String.valueOf(entry.file.getName())));
                    })))))
        .render();

    log.debug("end");

    return result;

  }

  private String imageResultView(String folderName, ImageBattleFolder folder) {
    log.debug("start");
    // System.err.println(folder.jsonGraph());

    String directory = "D:\\tech\\graphviz\\";
    String fileName = folderName;

    folder.writeGraphImage(directory, fileName);

    Function<ResultListEntry, DomContent> entryToResultViewTile = entry -> {
      return div().attr("title", entry.file.getName())
          .with(
              span(String.valueOf(entry.place)).withClass("rank"),
              span(entry.wins + " : " + entry.loses).withClass("winloss"),
              form()
                  .withAction("/reset/" + folderName + "/" + entry.file.getName())
                  .withMethod("post")
                  .with(input().withType("submit").withValue("Reset")),
              img().attr("src", FILES + "/" + folderName + "/" + entry.file.getName()));
    };

    String result = html().with(
        head().with(
            title("Results - " + folderName),
            link().withHref("/style/image_results.css").withRel("stylesheet").withType("text/css")),
        body().with(
            a("Battle").withHref("/battle/" + folderName),
            each(folder.getResultList(), entryToResultViewTile)//
        )//
    ).render();

    log.debug("end");

    return result;

  }

  public static HttpServletResponse musicFile(String directory, String filePath, Response result) {
    log.trace("start");
    HttpServletResponse raw = result.raw();
    raw.setContentType("audio/mpeg");
    try (ServletOutputStream outputStream = raw.getOutputStream()) {
      Files.copy(Paths.get(directory, filePath), outputStream);
      raw.flushBuffer();
    } catch (EofException eof) {
      log.debug("EOF exception on {}/{}", directory, filePath);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    log.trace("end");
    return raw;
  }

  public String audioBattle(Pair<File, File> pair, String folderName, File directory) {
    String file1 = pair.getKey().getAbsolutePath().replace(directory.getAbsolutePath(), "");
    String file2 = pair.getValue().getAbsolutePath().replace(directory.getAbsolutePath(), "");
    String template = getResource("/audio.html");
    return template.replace("#AUDIO_SOURCE_1#", FILES + "/" + folderName + "/" + file1)
        .replace("#AUDIO_SOURCE_2#", FILES + "/" + folderName + "/" + file2)
        .replace("#ID_1#", file1)
        .replace("#ID_2#", file2)
        .replace("#AUDIO_DESCRIPTION_1#", audioMetaData(pair.getKey()))
        .replace("#AUDIO_DESCRIPTION_2#", audioMetaData(pair.getValue()))
        .replace("#FOLDER_NAME#", folderName);
  }

  public String imageBattle(Pair<File, File> pair, String folderName, File directory) {
    String file1 = pair.getKey().getAbsolutePath().replace(directory.getAbsolutePath(), "");
    String file2 = pair.getValue().getAbsolutePath().replace(directory.getAbsolutePath(), "");
    String template = getResource("/image.html");
    return template.replace("#SOURCE_1#", FILES + "/" + folderName + "/" + file1)
        .replace("#SOURCE_2#", FILES + "/" + folderName + "/" + file2)
        .replace("#ID_1#", file1)
        .replace("#ID_2#", file2)
        .replace("#FOLDER_NAME#", folderName);
  }

  private String audioMetaData(File file) {
    MusicFile musicFile = MusicFile.create(file);
    return table().with(
        tr().with(td("Artist"), td(musicFile.getArtist())),
        tr().with(td("Album"), td(musicFile.getAlbum())),
        tr().with(td("Title"), td(musicFile.getTitle())))
        .render();
  }

  private String getResource(String resourcePath) {
    try (InputStream inputStream = MediaBattleWebApplication.class
        .getResourceAsStream(resourcePath)) {
      return IOUtils.toString(inputStream);

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String stackTraceString(Exception e) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return sw.toString(); // stack trace as a string
  }

}
