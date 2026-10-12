import { Request, Response, Router } from 'express';
import { ReviewRepository } from '../repository/catalog/ReviewRepository';
import { UserRepository } from '../repository/catalog/UserRepository';
import { ProductRepository } from '../repository/catalog/ProductRepository';
import { toPageResponse } from '../utils/pagination';

export function createReviewController(
  reviewRepository: ReviewRepository,
  userRepository: UserRepository,
  productRepository: ProductRepository
): Router {
  const router = Router();

  router.post('/', async (req: Request, res: Response) => {
    try {
      const review = req.body;
      if (!review.user || review.user.id == null) {
        return res.status(400).send();
      }
      if (!review.product || review.product.id == null) {
        return res.status(400).send();
      }

      const user = await userRepository.findById(review.user.id);
      const product = await productRepository.findById(review.product.id);

      if (!user || !product) {
        return res.status(400).send();
      }

      review.user = user;
      review.product = product;
      const saved = await reviewRepository.save(review);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/', async (req: Request, res: Response) => {
    try {
      const page = req.query.page ? parseInt(req.query.page as string, 10) : 0;
      const size = req.query.size ? parseInt(req.query.size as string, 10) : 20;

      let reviews;
      let total;

      if (req.query.userId) {
        const userId = parseInt(req.query.userId as string, 10);
        reviews = await reviewRepository.findByUserId(userId, { page, size });
        total = reviews.length; // or total count for user
      } else if (req.query.productId) {
        const productId = parseInt(req.query.productId as string, 10);
        reviews = await reviewRepository.findByProductId(productId, { page, size });
        total = reviews.length;
      } else {
        reviews = await reviewRepository.findAll({ page, size });
        total = await reviewRepository.count();
      }

      res.status(200).json(toPageResponse(reviews, total, page, size));
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const review = await reviewRepository.findById(id);
      if (!review) {
        return res.status(404).send();
      }
      res.status(200).json(review);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.put('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await reviewRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }

      if (req.body.user && req.body.user.id != null) {
        const user = await userRepository.findById(req.body.user.id);
        if (user) existing.user = user;
      }
      if (req.body.product && req.body.product.id != null) {
        const prod = await productRepository.findById(req.body.product.id);
        if (prod) existing.product = prod;
      }

      existing.rating = req.body.rating;
      existing.comment = req.body.comment;

      const saved = await reviewRepository.save(existing);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.delete('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await reviewRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }
      await reviewRepository.delete(existing);
      res.status(204).send();
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  return router;
}
