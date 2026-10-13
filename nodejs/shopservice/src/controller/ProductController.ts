import { Request, Response, Router } from 'express';
import { ProductRepository } from '../repository/catalog/ProductRepository';
import { toPageResponse } from '../utils/pagination';

export function createProductController(productRepository: ProductRepository): Router {
  const router = Router();

  router.post('/', async (req: Request, res: Response) => {
    try {
      const product = await productRepository.save(req.body);
      res.status(200).json(product);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/', async (req: Request, res: Response) => {
    try {
      const page = req.query.page ? parseInt(req.query.page as string, 10) : 0;
      const size = req.query.size ? parseInt(req.query.size as string, 10) : 20;
      const products = await productRepository.findAll({ page, size });
      const total = await productRepository.count();
      res.status(200).json(toPageResponse(products, total, page, size));
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const product = await productRepository.findById(id);
      if (!product) {
        return res.status(404).send();
      }
      res.status(200).json(product);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.put('/', async (req: Request, res: Response) => {
    try {
      const id = req.body.id;
      res.setHeader('Location', `/products/${id}`);
      res.status(201).send();
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.put('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await productRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }
      existing.name = req.body.name;
      existing.price = req.body.price;
      const saved = await productRepository.save(existing);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.delete('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await productRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }
      await productRepository.delete(existing);
      res.status(204).send();
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  return router;
}
