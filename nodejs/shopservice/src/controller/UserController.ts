import { Request, Response, Router } from 'express';
import { UserRepository } from '../repository/catalog/UserRepository';
import { toPageResponse } from '../utils/pagination';

export function createUserController(userRepository: UserRepository): Router {
  const router = Router();

  router.post('/', async (req: Request, res: Response) => {
    try {
      const user = await userRepository.save(req.body);
      res.status(200).json(user);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/', async (req: Request, res: Response) => {
    try {
      const page = req.query.page ? parseInt(req.query.page as string, 10) : 0;
      const size = req.query.size ? parseInt(req.query.size as string, 10) : 20;
      const users = await userRepository.findAll({ page, size });
      const total = await userRepository.count();
      res.status(200).json(toPageResponse(users, total, page, size));
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const user = await userRepository.findById(id);
      if (!user) {
        return res.status(404).send();
      }
      res.status(200).json(user);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.put('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await userRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }
      existing.username = req.body.username;
      existing.email = req.body.email;
      const saved = await userRepository.save(existing);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.delete('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await userRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }
      await userRepository.delete(existing);
      res.status(204).send();
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  return router;
}
