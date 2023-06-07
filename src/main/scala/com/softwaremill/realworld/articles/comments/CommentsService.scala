package com.softwaremill.realworld.articles.comments

import com.softwaremill.realworld.articles.comments.CommentsService.*
import com.softwaremill.realworld.articles.core.{ArticleSlug, ArticlesRepository}
import com.softwaremill.realworld.common.Exceptions
import com.softwaremill.realworld.common.Exceptions.{BadRequest, NotFound, Unauthorized}
import zio.{Task, ZIO, ZLayer}

class CommentsService(
    commentsRepository: CommentsRepository,
    articlesRepository: ArticlesRepository
):

  def addComment(slug: ArticleSlug, userId: Int, comment: String): Task[Comment] = for {
    articleId <- articleIdBySlug(slug)
    commentId <- commentsRepository.addComment(articleId, userId, comment)
    comment <- commentsRepository.findComment(commentId, userId).someOrFail(NotFound(CommentNotFoundMessage(commentId)))
  } yield comment

  def deleteComment(slug: ArticleSlug, userId: Int, commentId: Int): Task[Unit] = for {
    articleId <- articleIdBySlug(slug)
    tupleWithIds <- commentsRepository
      .findArticleAndAuthorIdsFromComment(commentId)
      .someOrFail(NotFound(ArticleAndAuthorIdsNotFoundMessage(commentId)))
    (commentAuthorId, commentArticleId) = tupleWithIds
    _ <- ZIO.fail(Unauthorized(CommentCannotBeRemoveMessage)).when(userId != commentAuthorId)
    _ <- ZIO.fail(BadRequest(CommentNotLinkedToSlugMessage(commentId, slug))).when(articleId != commentArticleId)
    _ <- commentsRepository.deleteComment(commentId)
  } yield ()

  def getCommentsFromArticle(slug: ArticleSlug, userIdOpt: Option[Int]): Task[List[Comment]] =
    articleIdBySlug(slug).flatMap(articleId =>
      userIdOpt match {
        case Some(userId) => commentsRepository.findComments(articleId, Some(userId))
        case None         => commentsRepository.findComments(articleId, None)
      }
    )

  private def articleIdBySlug(slug: ArticleSlug): Task[Int] =
    articlesRepository.findArticleIdBySlug(slug).someOrFail(NotFound(ArticleNotFoundMessage(slug)))

object CommentsService:
  private val ArticleNotFoundMessage: ArticleSlug => String = (slug: ArticleSlug) => s"Article with slug ${slug.value} doesn't exist."
  private val CommentNotFoundMessage: Int => String = (commentId: Int) => s"Comment with id=$commentId doesn't exist"
  private val CommentCannotBeRemoveMessage = "Can't remove the comment you're not an author of"
  private val CommentNotLinkedToSlugMessage: (Int, ArticleSlug) => String = (commentId: Int, slug: ArticleSlug) =>
    s"Comment with id=$commentId is not linked to slug ${slug.value}"
  private val ArticleAndAuthorIdsNotFoundMessage: Int => String = (commentId: Int) =>
    s"ArticleId or authorId for comment with id=$commentId doesn't exist"

  val live: ZLayer[CommentsRepository with ArticlesRepository, Nothing, CommentsService] =
    ZLayer.fromFunction(CommentsService(_, _))
