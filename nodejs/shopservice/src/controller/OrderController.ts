import { Request, Response, Router } from 'express';
import { OrderRepository } from '../repository/checkout/OrderRepository';
import { UserRepository } from '../repository/catalog/UserRepository';
import { ProductRepository } from '../repository/catalog/ProductRepository';
import { toPageResponse } from '../utils/pagination';

export function createOrderController(
  orderRepository: OrderRepository,
  userRepository: UserRepository,
  productRepository: ProductRepository
): Router {
  const router = Router();

  async function isUserValid(userId?: number): Promise<boolean> {
    if (userId == null) return false;
    const user = await userRepository.findById(userId);
    return user !== null;
  }

  async function isValidOrderItems(orderItems?: any[]): Promise<boolean> {
    if (orderItems != null && orderItems.length === 0) return false;
    if (orderItems != null) {
      for (const item of orderItems) {
        if (!item || item.productId == null) return false;
        const prod = await productRepository.findById(item.productId);
        if (!prod) return false;
      }
    }
    return true;
  }

  router.post('/', async (req: Request, res: Response) => {
    try {
      const order = req.body;
      const userValid = await isUserValid(order.userId);
      if (!userValid) {
        return res.status(400).send();
      }

      const itemsValid = await isValidOrderItems(order.orderItems);
      if (!itemsValid) {
        return res.status(400).send();
      }

      const saved = await orderRepository.save(order);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/', async (req: Request, res: Response) => {
    try {
      const page = req.query.page ? parseInt(req.query.page as string, 10) : 0;
      const size = req.query.size ? parseInt(req.query.size as string, 10) : 20;
      const orders = await orderRepository.findAll({ page, size });
      const total = await orderRepository.count();
      res.status(200).json(toPageResponse(orders, total, page, size));
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const order = await orderRepository.findById(id);
      if (!order) {
        return res.status(404).send();
      }
      res.status(200).json(order);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.put('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await orderRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }

      const userValid = await isUserValid(req.body.userId);
      if (userValid) {
        existing.userId = req.body.userId;
      }
      if (req.body.orderDate != null) {
        existing.orderDate = req.body.orderDate;
      }
      const itemsValid = await isValidOrderItems(req.body.orderItems);
      if (itemsValid && req.body.orderItems != null) {
        existing.orderItems = req.body.orderItems;
      }

      const saved = await orderRepository.save(existing);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.delete('/:id', async (req: Request, res: Response) => {
    try {
      const id = parseInt(req.params.id, 10);
      const existing = await orderRepository.findById(id);
      if (!existing) {
        return res.status(404).send();
      }
      await orderRepository.delete(existing);
      res.status(204).send();
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  return router;
}
