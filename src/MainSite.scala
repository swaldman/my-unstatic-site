
package my.unstatic.website

import scala.collection.*

import unstatic.*
import unstatic.ztapir.*
import unstatic.ztapir.simple.*

import unstatic.*, UrlPath.*

import java.nio.file.Path as JPath

import java.time.{Instant,ZoneId}

import untemplate.Untemplate.AnyUntemplate

import SimpleBlog.SyntheticUpdateAnnouncementSpec

object MainSite extends ZTSite.SingleStaticRootComposite( JPath.of("static") ):
  override val serverUrl : Abs    = Abs("https://test-unstatic.mchange.com/")
  override val basePath  : Rooted = Rooted.root

  case class MainLayoutInput( renderLocation : SiteLocation, mainContentHtml : String, sourceUntemplates : immutable.Seq[AnyUntemplate] = immutable.Seq.empty, singleItemRssSpec : Option[SingleItemRssSpec] = None )

  object MainBlog extends SimpleBlog:
    override type Site = MainSite.type
    override val site = MainSite.this
    override lazy val rssFeed = site.location( "/feed/index.rss" )
    override val feedTitle = "Getting Started Example"
    override val frontPage = site.location("/index.html")
    override val frontPageIdentifiers = super.frontPageIdentifiers ++ immutable.Set("home","homePage") // since we are using the blog as home
    override val maxFrontPageEntries = Some(5)
    override val timeZone = ZoneId.of("America/Montevideo")
    override def entryUntemplates =
      IndexFilter.fromIndex( IndexedUntemplates )
        .inOrBeneathPackage("my.unstatic.website.entries")
        .withNameLike( _.startsWith("entry_") )
        .untemplates
        .map( _.asInstanceOf[EntryUntemplate] )
    override def mediaPathPermalink( ut : untemplate.Untemplate[?,?] ) : MediaPathPermalink =
      import MediaPathPermalink.*
      overridable( yearMonthDayNameDir(timeZone), ut )

    override val revisionBinder : Option[RevisionBinder] = Some( RevisionBinder.GitByCommit(MainSite, JPath.of("."), siteRooted => Rel("public/").embedRoot(siteRooted)) )

    override val diffBinder : Option[DiffBinder] = Some( DiffBinder.JavaDiffUtils(MainSite) )

    override val syntheticUpdateAnnouncementSpec : Option[SyntheticUpdateAnnouncementSpec] = Some( SyntheticUpdateAnnouncementSpec( "Update-o-Bot", Instant.EPOCH ) )

    override val generateSingleItemRss : Boolean = true

    override val allItemFeedSiteRooted : Option[Rooted] = Some(Rooted("/all-item-feed/index.rss"))

    override def layoutEntry(input: Layout.Input.Entry) : String = layout_entry_html(input).text

    // overriding a def, but it's just a constant, so we override with val
    override val entrySeparator : String = entry_separator_html().text

    // here the blog shares the sites main overall layout
    override def layoutPage(input: Layout.Input.Page): String =
      val singleItemRssSpec =
        if input.sourceEntries.length == 1 then
          val info = input.sourceEntries.head.entryInfo
          info.singleItemRssSiteRooted.map( siteRooted => SingleItemRssSpec( siteRooted, info.mbTitle ) )
        else
          None
      val mainLayoutInput = MainLayoutInput( input.renderLocation, input.mainContentHtml, input.sourceEntries.map( _.entryUntemplate ), singleItemRssSpec )
      layout_main_html(mainLayoutInput).text

    /*
    override def renderMultiplePrologue( renderLocation : SiteLocation ) : String =
      if renderLocation == frontPage then
        latest_posts_html( renderLocation, this ).text
      else
        ""
    */

    object Archive:
      val location = site.location("/archive.html")
      case class Input( renderLocation : SiteLocation, entryUntemplatesResolved : immutable.SortedSet[EntryResolved] )

      val task = zio.ZIO.attempt {
         val contentsHtml = layout_archive_html( Input( location, entriesResolved ) ).text
         layout_main_html( MainLayoutInput( location, contentsHtml, Nil ) ).text
      }
      val endpointBinding = publicReadOnlyHtml( location, task, None, immutable.Set("archive"), resolveHashSpecials = true )
    end Archive

    override def endpointBindings : immutable.Seq[ZTEndpointBinding] = super.endpointBindings :+ Archive.endpointBinding

  end MainBlog

  // avoid conflicts, but early items in the lists take precedence over later items
  override val endpointBindingSources : immutable.Seq[ZTEndpointBinding.Source] = immutable.Seq( MainBlog )

object MainSiteGenerator extends ZTMain(MainSite)


